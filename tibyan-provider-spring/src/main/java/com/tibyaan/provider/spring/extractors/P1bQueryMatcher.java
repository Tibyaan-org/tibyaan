package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The P1b strict by-primary-key custom-query matcher (Volume 5 section 4.2.10). A custom {@code @Query}
 * method qualifies as by-primary-key load evidence ONLY when it is provably nothing but a by-primary-key
 * load: a root-entity select, with LEFT JOIN FETCH clauses only (an inner fetch is an existence filter,
 * refused), and a WHERE that is EXACTLY one equality between the entity's id field and the method's single
 * parameter, bound verbatim. Anything more, a projection, a native query, an unlocatable text, a second
 * predicate, an inner fetch, refuses. The tool reads a bounded JPQL token subset; it never interprets the
 * query language (section 4.2.10.4).
 *
 * <p>On real evidence the refusal is the rule's primary path: no custom query in either public benchmark
 * is both provably nothing-but-a-by-pk-load and feeds a discoverable read (pms Tenant fails on an inner
 * fetch; that is exactly what this matcher makes precise).
 */
final class P1bQueryMatcher {

    /** The verdict for one custom-query load terminal. */
    enum Kind {
        /** A verified by-primary-key select: qualifies as by-primary-key load evidence. */
        VERIFIED,
        /** Not a {@code @Query} at all, so P1b does not apply; the caller keeps its own message. */
        NOT_A_QUERY,
        /** A {@code @Query} that is present but does not prove a nothing-but-by-pk load: refused. */
        REFUSED
    }

    /**
     * @param kind   VERIFIED, NOT_A_QUERY, or REFUSED
     * @param reason the located refusal reason when REFUSED, else null
     */
    record Verdict(Kind kind, String reason) {

        static Verdict verified() {
            return new Verdict(Kind.VERIFIED, null);
        }

        static Verdict notAQuery() {
            return new Verdict(Kind.NOT_A_QUERY, null);
        }

        static Verdict refused(String reason) {
            return new Verdict(Kind.REFUSED, reason);
        }
    }

    // The bounded JPQL template (read as tokens, never interpreted): SELECT [DISTINCT] <alias> FROM <E>
    // <alias> <fetch clauses> WHERE <predicate>. The select item, the entity, the alias, the fetch region,
    // and the predicate are captured; each is then checked structurally.
    private static final Pattern TEMPLATE = Pattern.compile(
            "^\\s*SELECT\\s+(?:DISTINCT\\s+)?(?<select>[^\\s]+)\\s+FROM\\s+(?<entity>[\\w.]+)\\s+(?<alias>\\w+)"
                    + "\\s*(?<joins>.*?)\\s*WHERE\\s+(?<where>.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // The one permitted WHERE: exactly <alias>.<idField> = :name  or  <alias>.<idField> = ?1. Anchored, so
    // any further predicate (AND/OR, a second comparison, a function) fails the match and is refused.
    private static final Pattern ID_EQUALITY = Pattern.compile(
            "^(?<alias>\\w+)\\.(?<field>\\w+)\\s*=\\s*(?::(?<named>\\w+)|\\?1)$");

    // One LEFT JOIN FETCH clause: LEFT JOIN FETCH <path> [<alias>]. The fetch region must be zero or more
    // of these and nothing else. The optional alias must not be a join keyword, otherwise it would swallow
    // the next clause's leading LEFT and leave a stray inner JOIN FETCH in the remainder.
    private static final Pattern LEFT_JOIN_FETCH = Pattern.compile(
            "LEFT\\s+JOIN\\s+FETCH\\s+[\\w.]+(?:\\s+(?!LEFT\\b|JOIN\\b|INNER\\b|RIGHT\\b|WHERE\\b)\\w+)?",
            Pattern.CASE_INSENSITIVE);

    private P1bQueryMatcher() {
    }

    /**
     * Evaluate a repository query method as by-primary-key load evidence for the entity.
     *
     * @param queryMethod   the repository method carrying the load (the direct call, or the one-hop
     *                      delegate terminal)
     * @param entitySimple  the entity's simple name, the FROM target the JPQL must select of
     * @param idFieldName   the entity's actual {@code @Id} field name (never assumed to be "id")
     */
    static Verdict evaluate(MethodDeclaration queryMethod, String entitySimple, String idFieldName) {
        Optional<AnnotationExpr> query = queryMethod.getAnnotations().stream()
                .filter(a -> lastSegment(a.getNameAsString()).equals("Query"))
                .findFirst();
        if (query.isEmpty()) {
            return Verdict.notAQuery();
        }
        AnnotationExpr annotation = query.get();

        // P2: native queries reference tables and columns the tool has not mapped to E, so the structural
        // link is unprovable.
        if (isNative(annotation)) {
            return Verdict.refused("the load resolves through " + queryMethod.getNameAsString()
                    + ", a native @Query; native SQL is not tied to " + entitySimple
                    + " and its id, so it is not verifiable by-primary-key evidence (P1b, P2)");
        }
        // P2: the query text must be a literal string present in the annotation; a named-query reference
        // or a non-literal value is unlocatable and refuses.
        Optional<String> jpql = literalQuery(annotation);
        if (jpql.isEmpty()) {
            return Verdict.refused("the load resolves through " + queryMethod.getNameAsString()
                    + ", whose @Query text is not a readable literal (a named or externally-defined query); "
                    + "unlocatable query text is refused, never assumed (P1b, P2)");
        }
        // Condition 2 restated for the query: exactly one parameter, the id.
        if (queryMethod.getParameters().size() != 1) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString()
                    + " declares more than one parameter, so it is not a by-id load (P1b, condition 2)");
        }

        String text = jpql.get().replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        Matcher template = TEMPLATE.matcher(text);
        if (!template.matches()) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString()
                    + " does not fit the by-primary-key template SELECT <alias> FROM " + entitySimple
                    + " <alias> [LEFT JOIN FETCH ...] WHERE <alias>." + idFieldName + " = :param (P1b, P3)");
        }
        String select = template.group("select");
        String entity = lastSegment(template.group("entity"));
        String alias = template.group("alias");
        String joins = template.group("joins").trim();
        String where = template.group("where").trim();

        // P3: a single-entity select of the root. The sole select item is exactly the FROM alias, and the
        // FROM entity is E by simple name. A projection (new Dto(...), a field path, COUNT, or more than one
        // item) is not a load of E.
        if (!select.equals(alias)) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString() + " selects '"
                    + select + "', not the root entity alias '" + alias
                    + "'; a projection is not a load of " + entitySimple + " (P1b, P3)");
        }
        if (!entity.equals(entitySimple)) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString() + " selects FROM '"
                    + entity + "', not '" + entitySimple + "' (P1b, P3)");
        }

        // P4: every clause between FROM and WHERE is a LEFT JOIN FETCH. An inner JOIN FETCH is an existence
        // filter that drops the root when the association is absent, refused structurally from the text.
        if (!joins.isEmpty()) {
            Verdict joinVerdict = checkJoins(joins, queryMethod.getNameAsString());
            if (joinVerdict != null) {
                return joinVerdict;
            }
        }

        // P5, P6: the WHERE is exactly one equality between E's id field and the method's single parameter.
        Matcher equality = ID_EQUALITY.matcher(where);
        if (!equality.matches()) {
            return Verdict.refused("custom query on " + queryMethod.getNameAsString()
                    + " filters beyond the primary key (WHERE has more than the id equality); it is a "
                    + "filtered read, not a by-id load, and is deferred, not bound (P1b, P5)");
        }
        if (!equality.group("alias").equals(alias) || !equality.group("field").equals(idFieldName)) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString()
                    + " compares '" + equality.group("alias") + "." + equality.group("field")
                    + "', not the entity's id field '" + alias + "." + idFieldName + "' (P1b, P5)");
        }
        // P6: the parameter is the method's single parameter, bound verbatim (:name matching the parameter
        // or its @Param, or the positional ?1).
        String named = equality.group("named");
        if (named != null && !boundToSingleParameter(queryMethod, named)) {
            return Verdict.refused("the custom query " + queryMethod.getNameAsString()
                    + " binds ':" + named + "', which is not its single id parameter (P1b, P6)");
        }
        return Verdict.verified();
    }

    // The inner-fetch and non-LEFT-join refusals: the fetch region, stripped of every LEFT JOIN FETCH
    // clause, must be empty. A leftover `JOIN FETCH` is an inner fetch; any other leftover is a join the
    // template does not admit.
    private static Verdict checkJoins(String joins, String methodName) {
        String remainder = LEFT_JOIN_FETCH.matcher(joins).replaceAll(" ").trim();
        if (remainder.isEmpty()) {
            return null;
        }
        String upper = remainder.toUpperCase();
        if (upper.contains("JOIN FETCH")) {
            return Verdict.refused("custom query on " + methodName + " has an inner JOIN FETCH, an "
                    + "existence filter that excludes rows whose association is absent; it is not a by-id "
                    + "load and is deferred, not bound (P1b, P4)");
        }
        return Verdict.refused("custom query on " + methodName + " has a join that is not a LEFT JOIN "
                + "FETCH (a plain or conditional join); it is not a pure by-id load (P1b, P4)");
    }

    private static boolean isNative(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return false;
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (pair.getNameAsString().equals("nativeQuery")) {
                return pair.getValue().toString().equals("true");
            }
        }
        return false;
    }

    // The @Query's literal string value: a single-member @Query("...") or a value = "..." pair. A
    // non-literal value (a constant reference, a concatenation) is not readable text and returns empty.
    private static Optional<String> literalQuery(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single
                && single.getMemberValue() instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("value") && pair.getValue() instanceof StringLiteralExpr lit) {
                    return Optional.of(lit.asString());
                }
            }
        }
        return Optional.empty();
    }

    // P6: a named parameter :name is bound verbatim when it matches the method's single parameter name or
    // that parameter's @Param("name").
    private static boolean boundToSingleParameter(MethodDeclaration method, String named) {
        var parameter = method.getParameter(0);
        if (parameter.getNameAsString().equals(named)) {
            return true;
        }
        return parameter.getAnnotations().stream()
                .filter(a -> lastSegment(a.getNameAsString()).equals("Param"))
                .anyMatch(a -> a instanceof SingleMemberAnnotationExpr s
                        && s.getMemberValue() instanceof StringLiteralExpr lit
                        && lit.asString().equals(named));
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
