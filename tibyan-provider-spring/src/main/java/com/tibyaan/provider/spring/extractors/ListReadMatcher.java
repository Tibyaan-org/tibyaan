package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
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
    // The framework by-primary-key single reads: a wrapper built from one of these is a by-id read (the
    // 4.1/4.2 path), never E6a's list route.
    private static final Set<String> PK_READS = Set.of("findById", "getById", "getReferenceById");
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
        /**
         * E6a (section 4.11.7): a wrapper/envelope return whose tie to an entity cannot be proven without
         * reading the wrapper's internals or trusting its name. Counted as a refusal, never guessed.
         */
        OPAQUE,
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

        static Result opaque(String reason) {
            return new Result(Kind.OPAQUE, null, false, reason);
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
            // E6a (section 4.11.7): the return type is not a recognized collection container. It may be a
            // wrapper/envelope hiding a list read; make it visible and classified, or refuse it opaque.
            return evaluateEnvelope(returnType, container, source, resolver);
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

    // E6a (section 4.11.7): the envelope transparency rule. A read hidden behind an unrecognized wrapper
    // return type W is made VISIBLE and classified, never discovered by the wrapper. The framing is the
    // whole point: E6a changes a read from invisible to visible; it grants no discovery. Once visible the
    // read passes through 4.10 unchanged, so a filtered or transformed body stays deferred as filtered,
    // now countably. Visibility is never discovery: the ONLY match here is a clean, unfiltered framework
    // findAll of E behind a generic wrapper whose single type argument is E (fixture E6a-1, absent from the
    // benchmarks); every other collection read is a visible-but-deferred filtered read, and a wrapper with
    // no provable tie to an entity is OPAQUE_WRAPPER. The tie is never read from the wrapper's fields and
    // never trusted from its name.
    private Result evaluateEnvelope(ClassOrInterfaceType returnType, String wrapper,
                                    ServiceMethodSource source, TypeReferenceResolver resolver) {
        if (SCALARS.contains(wrapper)) {
            return Result.notList();
        }
        MethodDeclaration method = source.declaration();
        // A return type that itself resolves to an in-tree entity is a by-id (4.1) entity return, and a
        // wrapper whose single persistence access is a by-id read is a by-id (4.2) view; neither is E6a's
        // list route. These stay with the by-id path and are not surfaced here.
        Optional<String> asEntity = method.findCompilationUnit()
                .flatMap(cu -> resolver.resolveEntityKey(returnType.asString(), cu));
        if (asEntity.isPresent()) {
            return Result.notList();
        }
        Optional<BlockStmt> body = method.getBody();
        if (body.isEmpty()) {
            return Result.notList();
        }
        Map<String, String> repoFields = repositoryFields(source.service());
        List<MethodCallExpr> repoAccesses = body.get().findAll(MethodCallExpr.class).stream()
                .filter(call -> {
                    String field = receiverFieldName(call);
                    return field != null && repoFields.containsKey(field);
                })
                .toList();
        // No persistence access on a repository field: not a wrapper-list read E6a can see (a by-id view, a
        // pure computation, or a cross-service delegation). Leave it to the by-id path; do not surface it.
        if (repoAccesses.isEmpty()) {
            return Result.notList();
        }
        // A mutation on a repository field is a write, not a read (condition 7): not a read to surface.
        for (MethodCallExpr call : repoAccesses) {
            if (MUTATIONS.contains(call.getNameAsString())) {
                return Result.notList();
            }
        }
        if (repoAccesses.size() != 1) {
            return Result.deferred("the wrapper return '" + wrapper + "' is built from more than one "
                    + "repository access; a multi-read assembly is not a single provable list read "
                    + "(E6a, section 4.11.7)");
        }
        MethodCallExpr access = repoAccesses.get(0);
        String repoType = repoFields.get(receiverFieldName(access));
        // Cardinality first (V): E6a's list route surfaces COLLECTION reads. A single-entity access behind
        // a wrapper (a by-id shape: findById, selectById, or any repository method whose declared return is
        // not a collection) is a by-id candidate, owned by 4.1/4.2/P1b, and is left to the by-id path and
        // its entity outcome, never surfaced here as a list refusal or an opaque wrapper.
        if (!accessReturnsCollection(access, repoType)) {
            return Result.notList();
        }
        Optional<SpringDataRepo> repo = catalog.springDataRepo(repoType);
        // O, opaque refusal: a COLLECTION access whose repository does not resolve to a Spring Data
        // repository of an in-tree entity. The tie cannot be proven without reading the wrapper's internals
        // or trusting its name (fixture E6a-3). Counted, never guessed.
        if (repo.isEmpty() || repo.get().entityKey() == null) {
            return Result.opaque("the wrapper return '" + wrapper + "' is built from a collection access on '"
                    + repoType + "', which does not resolve to a Spring Data repository of an in-tree "
                    + "entity; the read's tie to an entity cannot be proven without reading the wrapper's "
                    + "internals or trusting its name (E6a, OPAQUE_WRAPPER)");
        }
        String elementKey = repo.get().entityKey();
        String elementSimple = lastSegment(elementKey);
        // V, visibility is not discovery. The only discovery is a clean unfiltered framework findAll of the
        // tied entity behind a GENERIC wrapper whose type argument is that entity (T1), the one shape where
        // the collection payload is provable without reading the wrapper. Every other collection read (a
        // derived query, a @Query, a selectAllByFilters, a transformed or aggregated body, or a clean
        // findAll behind a non-generic wrapper whose collection-ness is unprovable) is visible-but-deferred
        // as filtered, refused exactly as 4.10 refuses it. Never a wrong-entity: the tie is to the entity
        // the repository access proves, never the wrapper's name.
        boolean cleanFindAll = access.getNameAsString().equals(FIND_ALL)
                && access.getArguments().isEmpty()
                && hasNoDomainParameters(method)
                && returnFlowsFromAccess(body.get(), access);
        boolean genericTieToElement = singleTypeArgumentRaw(returnType)
                .flatMap(arg -> method.findCompilationUnit()
                        .flatMap(cu -> resolver.resolveEntityKey(arg, cu)))
                .map(elementKey::equals)
                .orElse(false);
        if (cleanFindAll && genericTieToElement) {
            return Result.match(elementKey, false);
        }
        return Result.deferred("the read of " + elementSimple + " is seen through the wrapper '" + wrapper
                + "' (E6a visibility); its body is not a clean unfiltered findAll of " + elementSimple
                + " behind a generic wrapper of it, so it is deferred as a filtered or transformed list "
                + "read, refused exactly as any other (E6a, then section 4.10)");
    }

    // Whether the persistence access is a COLLECTION read (a list-shaped read E6a's list route surfaces),
    // decided from the repository method's DECLARED return type, never guessed from the method name. The
    // framework findAll is a collection by contract; a framework by-primary-key read is a single; any other
    // repository method is a collection only when its declared return type is a recognized container. A
    // method the tool cannot locate on the repository interface is treated as a single read (left to the
    // by-id path), never surfaced as a list refusal.
    private boolean accessReturnsCollection(MethodCallExpr access, String repoType) {
        String name = access.getNameAsString();
        if (name.equals(FIND_ALL)) {
            return true;
        }
        if (PK_READS.contains(name) || repoType == null) {
            return false;
        }
        int arguments = access.getArguments().size();
        Optional<ClassOrInterfaceDeclaration> declaration = catalog.declaration(repoType);
        if (declaration.isEmpty()) {
            return false;
        }
        for (MethodDeclaration method : declaration.get().getMethods()) {
            if (method.getNameAsString().equals(name) && method.getParameters().size() == arguments) {
                return CONTAINERS.contains(lastSegment(method.getType().asString()));
            }
        }
        return false;
    }

    // Every parameter is a framework paging type (Pageable or Sort); no domain parameter that would make
    // the read filtered.
    private static boolean hasNoDomainParameters(MethodDeclaration method) {
        for (Parameter parameter : method.getParameters()) {
            if (!PAGING_TYPES.contains(lastSegment(parameter.getType().asString()))) {
                return false;
            }
        }
        return true;
    }

    // The body is a single return whose expression subtree contains the persistence access: the collection
    // provably flows out of the method, wrapped, without an intervening statement that could transform it.
    private static boolean returnFlowsFromAccess(BlockStmt body, MethodCallExpr access) {
        List<Statement> statements = body.getStatements();
        if (statements.size() != 1 || !statements.get(0).isReturnStmt()) {
            return false;
        }
        ReturnStmt returnStmt = statements.get(0).asReturnStmt();
        return returnStmt.getExpression()
                .map(expression -> expression.findAll(MethodCallExpr.class).contains(access))
                .orElse(false);
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
