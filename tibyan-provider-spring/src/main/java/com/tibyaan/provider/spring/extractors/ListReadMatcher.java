package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The list-read matcher (Volume 5 section 4.10). Given a public {@code @Service} method, it decides
 * whether the method is the unfiltered list-all of an entity: a collection of an in-tree {@code @Entity}
 * returned by delegating verbatim to the framework {@code findAll} on a Spring Data repository of that
 * entity, with no domain parameter, no transformation, and no mutation.
 *
 * <p>Everything here is structural. The method name carries no weight (a {@code findAll}, {@code getAll},
 * or any name yields the same verdict); only the framework {@code findAll} contract on a recognized
 * Spring Data supertype counts. A collection method that does not match is not silently dropped: it is a
 * named deferral (filtered, view-returning, or a non-framework terminal), never a guess (section 4.10.6).
 */
public final class ListReadMatcher {

    // Condition 2: the recognized collection containers whose single type argument is the element.
    private static final Set<String> CONTAINERS =
            Set.of("List", "Set", "Collection", "Iterable", "Page", "Slice");
    // A page envelope wire shape; a read whose return is one of these carries the `paged` disclosure.
    private static final Set<String> ENVELOPES = Set.of("Page", "Slice");
    // Condition 3: the framework paging types tolerated as a single unmodeled, forwarded parameter.
    private static final Set<String> PAGING_TYPES = Set.of("Pageable", "Sort");
    // The framework by-collection read; the only terminal this rule accepts (condition 4).
    private static final String FIND_ALL = "findAll";
    // Condition 7: a list method that performs any of these is a write, not a read.
    private static final Set<String> MUTATIONS = Set.of(
            "save", "saveAll", "insert", "update", "delete", "deleteById", "deleteAll",
            "persist", "merge", "flush");
    // Element type simple names that are scalars, not domain types: a List<String> is not a list read
    // of an entity or a view, so it is not reported at all (neither modeled nor deferred).
    private static final Set<String> SCALARS = Set.of(
            "String", "Integer", "Long", "Short", "Byte", "Boolean", "Character", "Double", "Float",
            "BigDecimal", "BigInteger", "UUID", "Object", "Number", "Void", "Date", "LocalDate",
            "LocalDateTime", "LocalTime", "Instant", "OffsetDateTime", "ZonedDateTime");

    private final RepositoryCatalog catalog;

    public ListReadMatcher(RepositoryCatalog catalog) {
        this.catalog = catalog;
    }

    /** The verdict for one method. */
    public enum Kind {
        /** A clean unfiltered list-all of {@link Result#entity}. */
        MATCH,
        /** A collection read the phase does not model, named with {@link Result#reason}. */
        DEFERRED,
        /** Not a list-of-entity-or-view read at all (a scalar collection, a raw type, or not a collection). */
        NOT_LIST
    }

    /**
     * @param kind   MATCH, DEFERRED, or NOT_LIST
     * @param entity the element entity's key (FQCN) when MATCH, else null (section 4.12.4)
     * @param paged  true when the wire is a page envelope (Page or Slice), so the `paged` disclosure applies
     * @param reason the deferral reason when DEFERRED, else null
     */
    public record Result(Kind kind, String entity, boolean paged, String reason) {

        static Result match(String entity, boolean paged) {
            return new Result(Kind.MATCH, entity, paged, null);
        }

        static Result deferred(String reason) {
            return new Result(Kind.DEFERRED, null, false, reason);
        }

        static Result notList() {
            return new Result(Kind.NOT_LIST, null, false, null);
        }
    }

    /**
     * Evaluate a service method against rule 4.10. The element type is resolved to a single in-tree
     * entity key by the two-stage resolver (section 4.12.4), so the element is classified as an entity
     * (a match candidate, carrying its key), a non-entity or unresolvable domain type (a deferred view
     * list), or a scalar (not a list read). Identity is by key, never by a colliding simple name.
     */
    public Result evaluate(ServiceMethodSource source, TypeReferenceResolver resolver) {
        MethodDeclaration method = source.declaration();

        // Condition 2: the return type is a recognized collection container of a single type argument.
        if (!(method.getType() instanceof ClassOrInterfaceType returnType)) {
            return Result.notList();
        }
        String container = returnType.getNameAsString();
        if (!CONTAINERS.contains(container)) {
            return Result.notList();
        }
        Optional<String> elementArg = singleTypeArgumentRaw(returnType);
        if (elementArg.isEmpty()) {
            return Result.notList();
        }
        String elementRaw = elementArg.get();
        String elementSimple = lastSegment(elementRaw);
        if (SCALARS.contains(elementSimple)) {
            return Result.notList();
        }
        // Section 4.12.4: resolve the element to a single in-tree entity key. A non-entity, an
        // unresolvable library type, or an ambiguous cross-context reference is not a match candidate;
        // it is a deferred view or projection list read, never guessed into an entity.
        Optional<String> elementKey = method.findCompilationUnit()
                .flatMap(cu -> resolver.resolveEntityKey(elementRaw, cu));
        if (elementKey.isEmpty()) {
            return Result.deferred("returns a collection of '" + elementSimple + "', which is not an "
                    + "in-tree @Entity; deferred view-returning or projection list read (section 4.10.6)");
        }
        String elementName = elementKey.get();

        // From here the element is an in-tree entity; the method is a list-all candidate for it.
        boolean paged = ENVELOPES.contains(container);
        Optional<BlockStmt> body = method.getBody();
        if (body.isEmpty()) {
            return Result.deferred("method has no body to analyze (condition 4)");
        }
        Map<String, String> repoFields = repositoryFields(source.service());

        // Condition 7: a mutation anywhere on a repository field is a write, not a read.
        for (MethodCallExpr call : body.get().findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(call);
            if (field != null && repoFields.containsKey(field) && MUTATIONS.contains(call.getNameAsString())) {
                return Result.deferred("body performs a mutation '" + call.getNameAsString()
                        + "' on a repository field; a list method that writes is not a read (condition 7)");
            }
        }

        // Condition 3: no parameters, or exactly one framework paging parameter. Any domain parameter is
        // a filtered read, deferred; its name is never copied into an input (section 4.10.2). Domain and
        // paging parameters are separated so a `findByX(domain, Pageable)` names its domain parameter.
        String pagingParam = null;
        int pagingCount = 0;
        List<String> domainParams = new ArrayList<>();
        for (com.github.javaparser.ast.body.Parameter parameter : method.getParameters()) {
            if (PAGING_TYPES.contains(lastSegment(parameter.getType().asString()))) {
                pagingCount++;
                pagingParam = parameter.getNameAsString();
            } else {
                domainParams.add(parameter.getNameAsString());
            }
        }
        if (!domainParams.isEmpty()) {
            return Result.deferred("takes domain parameter(s) " + domainParams + "; deferred filtered "
                    + "list read (condition 3)");
        }
        if (pagingCount > 1) {
            return Result.deferred("takes more than one framework paging parameter (condition 3)");
        }

        // Conditions 4, 5, 6: the body is exactly one return whose expression is the framework findAll on
        // a Spring Data repository of the element entity, with the paging parameter forwarded verbatim
        // (or no argument), and nothing wrapped around it.
        List<Statement> statements = body.get().getStatements();
        if (statements.size() != 1 || !statements.get(0).isReturnStmt()) {
            return Result.deferred("body is not a single return of the framework findAll; a transformed, "
                    + "filtered, or multi-statement body is deferred (condition 6)");
        }
        Optional<Expression> returned = statements.get(0).asReturnStmt().getExpression();
        if (returned.isEmpty() || !returned.get().isMethodCallExpr()) {
            return Result.deferred("the returned expression is not a repository call (condition 6)");
        }
        MethodCallExpr call = returned.get().asMethodCallExpr();
        if (!call.getNameAsString().equals(FIND_ALL)) {
            return Result.deferred("the load terminal is '" + call.getNameAsString() + "', not the "
                    + "framework findAll; a derived or @Query collection method is deferred (condition 4)");
        }
        String field = receiverFieldName(call);
        String repoType = field == null ? null : repoFields.get(field);
        if (repoType == null) {
            return Result.deferred("findAll is not called on a repository field of the service (condition 4)");
        }
        Optional<SpringDataRepo> repo = catalog.springDataRepo(repoType);
        if (repo.isEmpty() || repo.get().entityKey() == null
                || !repo.get().entityKey().equals(elementName)) {
            return Result.deferred("the findAll target '" + repoType + "' is not a Spring Data repository "
                    + "of " + elementSimple + " (condition 4)");
        }
        // Condition 5: verbatim paging argument, or no argument for a no-parameter list-all.
        if (pagingParam == null) {
            if (!call.getArguments().isEmpty()) {
                return Result.deferred("findAll is called with an argument but the method declares no "
                        + "paging parameter (condition 5)");
            }
        } else if (call.getArguments().size() != 1 || !isNameExpr(call.getArgument(0), pagingParam)) {
            return Result.deferred("findAll does not forward the paging parameter '" + pagingParam
                    + "' verbatim (condition 5)");
        }
        // Condition 7: the load is the only repository access in the body.
        long repoCalls = body.get().findAll(MethodCallExpr.class).stream()
                .filter(c -> {
                    String f = receiverFieldName(c);
                    return f != null && repoFields.containsKey(f);
                })
                .count();
        if (repoCalls != 1) {
            return Result.deferred("body contains more than one repository access (condition 7)");
        }

        return Result.match(elementName, paged);
    }

    // The single type argument as written (possibly qualified), or empty for a raw type or a
    // multi-argument generic. The resolver strips generics and narrows it to an in-tree key.
    private static Optional<String> singleTypeArgumentRaw(ClassOrInterfaceType type) {
        return type.getTypeArguments()
                .filter(args -> args.size() == 1)
                .map(args -> args.get(0).asString());
    }

    // The service's fields whose declared type resolves to a repository (Spring Data, or a hand-written
    // interface with a @Repository implementation), mapped from field name to declared type simple name.
    private Map<String, String> repositoryFields(ClassOrInterfaceDeclaration type) {
        Map<String, String> fields = new HashMap<>();
        for (FieldDeclaration declaration : type.getFields()) {
            String fieldType = lastSegment(declaration.getElementType().asString());
            if (catalog.isRepositoryType(fieldType)) {
                for (VariableDeclarator variable : declaration.getVariables()) {
                    fields.put(variable.getNameAsString(), fieldType);
                }
            }
        }
        return fields;
    }

    // The service field name a call is invoked on, whether written `field.m(...)` or `this.field.m(...)`.
    private static String receiverFieldName(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return null;
        }
        Expression s = scope.get();
        if (s.isNameExpr()) {
            return s.asNameExpr().getNameAsString();
        }
        if (s.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccess = s.asFieldAccessExpr();
            if (fieldAccess.getScope().isThisExpr()) {
                return fieldAccess.getNameAsString();
            }
        }
        return null;
    }

    private static boolean isNameExpr(Expression expr, String name) {
        return expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals(name);
    }

    private static String lastSegment(String name) {
        String trimmed = name.trim();
        int open = trimmed.indexOf('<');
        if (open >= 0) {
            trimmed = trimmed.substring(0, open);
        }
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(dot + 1) : trimmed;
    }
}
