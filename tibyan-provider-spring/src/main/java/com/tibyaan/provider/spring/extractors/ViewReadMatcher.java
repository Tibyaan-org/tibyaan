package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The DTO-aware read matcher (Volume 5 section 4.2, conditions 4 to 9). Given a service method that
 * has already passed conditions 1 to 3 (public {@code @Service}, one parameter mapping to the id
 * type, non-void return that is not the entity), it decides whether the method backs a read of the
 * entity by structural evidence: a by-primary-key load of the entity followed by exactly one mapping
 * step that derives the returned view from the loaded entity.
 *
 * <p>Everything here is structural. Names of views, services, and repository methods carry no
 * weight; the only names trusted are framework contracts (the Spring Data supertypes and the
 * framework by-primary-key methods). Any incompleteness, an unresolvable callee, a non-verbatim
 * argument, a second repository access, a custom-query terminal, is a near miss with a located
 * reason, never a guess (section 4.2.7).
 */
public final class ViewReadMatcher {

    private static final Set<String> PK_READS = Set.of("findById", "getById", "getReferenceById");
    private static final Set<String> UNWRAP = Set.of("orElseThrow", "orElse", "get");
    private static final Set<String> MUTATIONS = Set.of(
            "save", "saveAll", "insert", "update", "delete", "deleteById", "deleteAll",
            "persist", "merge", "flush");

    private final RepositoryCatalog catalog;
    private final JavaTypeMapper typeMapper;
    private final TypeReferenceResolver resolver;

    public ViewReadMatcher(RepositoryCatalog catalog, JavaTypeMapper typeMapper,
                           TypeReferenceResolver resolver) {
        this.catalog = catalog;
        this.typeMapper = typeMapper;
        this.resolver = resolver;
    }

    public MatchResult evaluate(MethodDeclaration method, ClassOrInterfaceDeclaration service, ReadIntent intent) {
        Optional<BlockStmt> maybeBody = method.getBody();
        if (maybeBody.isEmpty()) {
            return MatchResult.nearMiss("method has no body to analyze");
        }
        BlockStmt body = maybeBody.get();
        String param = method.getParameter(0).getNameAsString();
        String entity = intent.entityName();
        String view = method.getType().asString();

        Map<String, String> repoFields = repositoryFields(service);
        List<MethodCallExpr> repoCalls = repositoryCalls(body, repoFields.keySet());

        for (MethodCallExpr call : repoCalls) {
            if (MUTATIONS.contains(call.getNameAsString())) {
                return MatchResult.nearMiss("body performs a mutation '" + call.getNameAsString()
                        + "' on a repository field; a read binds to no write (condition 6)");
            }
        }
        if (repoCalls.isEmpty()) {
            // Section 4.13.3: the load may sit exactly one PRIVATE same-class hop away. The trace steps
            // through it under the same disciplines, or refuses. When no such helper is there, the
            // pre-phase refusal is returned verbatim, so a delegation-only facade reads exactly as before.
            return evaluateThroughPrivateHelper(method, service, intent, body, param, view, repoFields);
        }
        if (repoCalls.size() > 1) {
            return MatchResult.nearMiss("body contains more than one repository access (condition 6); "
                    + "a composite assembly is deferred pattern P2");
        }

        MethodCallExpr load = repoCalls.get(0);
        if (!isVerbatimArgument(load, param)) {
            return MatchResult.nearMiss("the load argument is not the id parameter '" + param
                    + "' verbatim (condition 5)");
        }

        String repoType = repoFields.get(receiverFieldName(load));
        MatchResult loadEvidence = checkByPrimaryKeyLoad(load, repoType, entity, intent.entityKey(),
                intent.idType(), intent.idInputName());
        if (!loadEvidence.matched()) {
            return loadEvidence;
        }

        return checkShapeAndMapping(body, load, service, entity, view, loadEvidence.explanation());
    }

    // The private-helper-hop trace (Volume 5, section 4.13.3), the general core of the mixed shape. The
    // public read's own body performs no persistence access because the load sits one PRIVATE same-class
    // hop away. The trace steps through exactly that one hop, pins the helper to a single declaration,
    // and holds the helper to the same disciplines section 4.2 holds a service body to. It grants no new
    // latitude: the load must still be a by-primary-key read of THIS entity, resolved by key.
    private MatchResult evaluateThroughPrivateHelper(MethodDeclaration method,
                                                     ClassOrInterfaceDeclaration service, ReadIntent intent,
                                                     BlockStmt body, String param, String view,
                                                     Map<String, String> repoFields) {
        // Condition 1: the load position is a call on the method's own class taking the id verbatim.
        List<MethodCallExpr> helperCalls = new ArrayList<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (isOwnMethodCall(call) && isVerbatimArgument(call, param)) {
                helperCalls.add(call);
            }
        }
        if (helperCalls.isEmpty()) {
            // The pre-phase refusal, returned verbatim: nothing loads here at all.
            return MatchResult.nearMiss("body contains no persistence access, so there is no load to "
                    + "verify (condition 4); a delegation-only facade is out of scope");
        }
        if (helperCalls.size() > 1) {
            return MatchResult.nearMiss("body calls more than one same-class method with the id "
                    + "parameter, so the load position is not unique (section 4.13.3, condition 1)");
        }
        MethodCallExpr helperCall = helperCalls.get(0);
        String helperName = helperCall.getNameAsString();

        // Condition 1a: the call must pin to EXACTLY ONE private declaration on the method's own class.
        // The tool does not adjudicate an overloaded or shadowed helper set; it refuses rather than read
        // one candidate's body while the id could dispatch to another. This is the never-guess bar
        // (section 4.12.4, step d) applied to a same-class method call.
        List<MethodDeclaration> sameName = new ArrayList<>();
        for (MethodDeclaration declared : service.getMethods()) {
            if (declared.getNameAsString().equals(helperName)) {
                sameName.add(declared);
            }
        }
        List<MethodDeclaration> privates = new ArrayList<>();
        for (MethodDeclaration declared : sameName) {
            if (declared.hasModifier(Modifier.Keyword.PRIVATE)) {
                privates.add(declared);
            }
        }
        if (privates.isEmpty()) {
            return MatchResult.nearMiss("the load is delegated to '" + helperName + "', which is not a "
                    + "private method on the service's own class (section 4.13.3, condition 5); a "
                    + "cross-class delegation is out of scope");
        }
        if (sameName.size() > 1) {
            return MatchResult.nearMiss("the helper call '" + helperName + "' does not resolve to a "
                    + "single private declaration: the class declares " + sameName.size() + " methods of "
                    + "that name, an overloaded or shadowed set the tool will not adjudicate "
                    + "(section 4.13.3, condition 1a); it refuses rather than read one candidate's body");
        }
        MethodDeclaration helper = privates.get(0);

        // Condition 2: the helper takes the id and returns E or Optional<E>, resolved by key.
        if (helper.getParameters().size() != 1) {
            return MatchResult.nearMiss("private helper '" + helperName + "' does not take exactly one "
                    + "parameter (section 4.13.3, condition 2)");
        }
        if (!singleParamMapsTo(helper, intent.idType())) {
            return MatchResult.nearMiss("private helper '" + helperName + "' does not take the entity's "
                    + "id type (section 4.13.3, condition 2)");
        }
        if (!returnsEntityOrOptionalByKey(helper, intent.entityKey())) {
            return MatchResult.nearMiss("private helper '" + helperName + "' does not return "
                    + intent.entityName() + " or Optional<" + intent.entityName() + "> (section 4.13.3, "
                    + "condition 2); the helper's return type resolves to a different entity or to none");
        }
        Optional<BlockStmt> helperBody = helper.getBody();
        if (helperBody.isEmpty()) {
            return MatchResult.nearMiss("private helper '" + helperName + "' has no body to analyze "
                    + "(section 4.13.3, condition 2)");
        }

        // Conditions 2 and 3: the helper's body is exactly the clean by-primary-key load and nothing else.
        MatchResult loadEvidence = checkHelperBody(helper, helperBody.get(), intent, repoFields);
        if (!loadEvidence.matched()) {
            return loadEvidence;
        }

        // Condition 4: back in the public method, the helper's value feeds exactly one resolvable
        // mapping step, under section 4.2's conditions 7 to 9, unchanged.
        return checkShapeAndMapping(body, helperCall, service, intent.entityName(), view,
                loadEvidence.explanation());
    }

    // Conditions 2 and 3: the helper performs exactly one persistence access, the by-primary-key load of
    // THIS entity with the id forwarded verbatim, and its body contains NO other statement, in any
    // position. A guard (the soft-delete check among them), a filter, a mutation, a second access, or a
    // further delegation all disqualify: the tool cannot verify such a guard is benign without
    // interpreting its meaning, which it must never do, so it refuses.
    private MatchResult checkHelperBody(MethodDeclaration helper, BlockStmt body, ReadIntent intent,
                                        Map<String, String> repoFields) {
        String name = helper.getNameAsString();
        String helperParam = helper.getParameter(0).getNameAsString();
        List<MethodCallExpr> repoCalls = repositoryCalls(body, repoFields.keySet());

        for (MethodCallExpr call : repoCalls) {
            if (MUTATIONS.contains(call.getNameAsString())) {
                return MatchResult.nearMiss("private helper '" + name + "' performs a mutation '"
                        + call.getNameAsString() + "' on a repository field; a read binds to no write "
                        + "(section 4.13.3, condition 3)");
            }
        }
        if (repoCalls.isEmpty()) {
            return MatchResult.nearMiss("private helper '" + name + "' contains no persistence access, "
                    + "so there is no load to verify (section 4.13.3, condition 2); a helper that "
                    + "delegates further is read no deeper");
        }
        if (repoCalls.size() > 1) {
            return MatchResult.nearMiss("private helper '" + name + "' contains more than one repository "
                    + "access (section 4.13.3, conditions 2 and 3)");
        }
        MethodCallExpr load = repoCalls.get(0);
        if (!isVerbatimArgument(load, helperParam)) {
            return MatchResult.nearMiss("private helper '" + name + "' does not forward its id parameter '"
                    + helperParam + "' verbatim to the load (section 4.13.3, condition 2)");
        }
        // Condition 3, the strict extra-statement refusal, worded so it names the defect accurately
        // whether the guard sits before or after the load.
        if (!isBareLoadBody(body, load)) {
            return MatchResult.nearMiss("private helper '" + name + "' contains a statement beyond the "
                    + "by-primary-key load and its Optional unwrap (section 4.13.3, condition 3); the "
                    + "tool cannot tell a soft-delete guard from an authorization or business filter "
                    + "without interpreting it, so it refuses rather than present a filtered read as a "
                    + "plain by-id read");
        }
        String repoType = repoFields.get(receiverFieldName(load));
        return checkByPrimaryKeyLoad(load, repoType, intent.entityName(), intent.entityKey(),
                intent.idType(), intent.idInputName());
    }

    // (checkByPrimaryKeyLoad from the private-helper hop shares the same signature as the direct call.)

    // Condition 3: the body is exactly the load (with its Optional unwrap), in one of the two shapes.
    // Any additional statement, in any position, means this is not a bare load.
    private static boolean isBareLoadBody(BlockStmt body, MethodCallExpr load) {
        List<Statement> statements = body.getStatements();
        Expression unwrapped = unwrappedLoadValue(load);
        if (statements.size() == 1) {
            if (!statements.get(0).isReturnStmt()) {
                return false;
            }
            Optional<Expression> returned = statements.get(0).asReturnStmt().getExpression();
            return returned.isPresent() && returned.get() == unwrapped;
        }
        if (statements.size() == 2) {
            Optional<String> local = localFromLoad(statements.get(0), load);
            if (local.isEmpty() || !statements.get(1).isReturnStmt()) {
                return false;
            }
            Optional<Expression> returned = statements.get(1).asReturnStmt().getExpression();
            return returned.isPresent() && isNameExpr(returned.get(), local.get());
        }
        return false;
    }

    // A call on the method's own class: written bare, or as this.m(...).
    private static boolean isOwnMethodCall(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        return scope.isEmpty() || scope.get().isThisExpr();
    }

    // Section 4.13.3, condition 2, re-anchored to the key resolver: the helper's declared return type
    // (or its Optional type argument) must resolve to the intent's entity BY KEY, never by simple name,
    // so a helper returning a same-named entity from another bounded context can never satisfy it.
    private boolean returnsEntityOrOptionalByKey(MethodDeclaration helper, String entityKey) {
        String returnType = helper.getType().asString();
        String target = returnType;
        if (lastSegment(stripGenerics(returnType)).equals("Optional")) {
            int open = returnType.indexOf('<');
            int close = returnType.lastIndexOf('>');
            if (open < 0 || close <= open) {
                return false;
            }
            target = returnType.substring(open + 1, close).trim();
        }
        String resolve = target;
        return helper.findCompilationUnit()
                .flatMap(unit -> resolver.resolveEntityKey(resolve, unit))
                .map(entityKey::equals)
                .orElse(false);
    }

    // Condition 4: the load is a by-primary-key read of E, direct (4a) or one-hop (4b). Whether the
    // repository is a repository of THIS entity is decided by key (section 4.12.4): a colliding simple
    // name binds to the right entity or refuses, never both. The simple name is used only in messages.
    private MatchResult checkByPrimaryKeyLoad(MethodCallExpr load, String repoType, String entity,
                                              String entityKey, Type idType, String idFieldName) {
        String loadMethod = load.getNameAsString();
        Optional<SpringDataRepo> direct = catalog.springDataRepo(repoType);
        if (direct.isPresent()) {
            SpringDataRepo repo = direct.get();
            if (!isRepositoryOf(repo, entityKey)) {
                return MatchResult.nearMiss("the load is a by-primary-key read of '"
                        + repo.entitySimpleName() + "', not '" + entity + "' (condition 4)");
            }
            if (!idTypeMatches(repo.idTypeAsWritten(), idType)) {
                return MatchResult.nearMiss("the repository '" + repoType + "' has id type '"
                        + repo.idTypeAsWritten() + "', which does not match the entity's id (condition 4)");
            }
            if (!PK_READS.contains(loadMethod)) {
                // P1b (section 4.2.10): a custom @Query on the Spring Data repository may serve as the
                // by-primary-key load evidence when it is provably nothing but a by-pk select. Anything
                // else refuses with its P1b reason; a non-@Query method keeps the framework-read message.
                return p1bDirect(repoType, loadMethod, entity, idFieldName);
            }
            return MatchResult.match(null, "loads " + entity + " by primary key directly through "
                    + repoType + "#" + loadMethod + ", " + repoType + " extends a Spring Data repository of "
                    + entity + " (" + repo.idTypeAsWritten() + ")");
        }
        return checkOneHop(repoType, loadMethod, entity, entityKey, idType, idFieldName);
    }

    // P1b for the direct (4a) load: consult the query method declared on the Spring Data repository. A
    // verified by-primary-key @Query is a match; a present-but-refused @Query names its P1b reason; a
    // method with no @Query keeps the framework-by-primary-key-read message.
    private MatchResult p1bDirect(String repoType, String loadMethod, String entity, String idFieldName) {
        Optional<MethodDeclaration> queryMethod = catalog.declaration(repoType)
                .flatMap(decl -> singleParamMethod(decl, loadMethod));
        if (queryMethod.isPresent()) {
            P1bQueryMatcher.Verdict verdict = P1bQueryMatcher.evaluate(queryMethod.get(), entity, idFieldName);
            if (verdict.kind() == P1bQueryMatcher.Kind.VERIFIED) {
                return MatchResult.match(null, "loads " + entity + " by primary key directly through "
                        + repoType + "#" + loadMethod + ", a custom @Query verified as a by-primary-key "
                        + "select of " + entity + " (P1b)");
            }
            if (verdict.kind() == P1bQueryMatcher.Kind.REFUSED) {
                return MatchResult.nearMiss(verdict.reason());
            }
        }
        return MatchResult.nearMiss("'" + loadMethod + "' on '" + repoType
                + "' is not a framework by-primary-key read (condition 4)");
    }

    // P1b for the one-hop delegate (4b): consult the delegate's query method on its Spring Data repository.
    // Returns null when the query is VERIFIED (the caller continues to its entity-of and forward checks), a
    // nearMiss carrying the P1b reason when REFUSED, and the framework-read message when there is no @Query.
    private MatchResult p1bDelegate(String delegateRepoType, String delegateMethod, String entity,
                                    String idFieldName, String repoType, String loadMethod) {
        Optional<MethodDeclaration> queryMethod = catalog.declaration(delegateRepoType)
                .flatMap(decl -> singleParamMethod(decl, delegateMethod));
        if (queryMethod.isPresent()) {
            P1bQueryMatcher.Verdict verdict = P1bQueryMatcher.evaluate(queryMethod.get(), entity, idFieldName);
            if (verdict.kind() == P1bQueryMatcher.Kind.VERIFIED) {
                return null;
            }
            if (verdict.kind() == P1bQueryMatcher.Kind.REFUSED) {
                return MatchResult.nearMiss(verdict.reason());
            }
        }
        return MatchResult.nearMiss("the load resolves through " + repoType + "#" + loadMethod
                + ", whose implementation delegates to " + delegateRepoType + "#" + delegateMethod
                + ", not a framework by-primary-key read; by-primary-key evidence unavailable, "
                + "deferred pattern P1b");
    }

    // Whether a Spring Data repository is a repository of the entity with this key (section 4.12.4).
    private static boolean isRepositoryOf(SpringDataRepo repo, String entityKey) {
        return repo.entityKey() != null && repo.entityKey().equals(entityKey);
    }

    // Condition 4b: the load calls a hand-written repository method whose single @Repository
    // implementation delegates in exactly one return statement to a Spring Data by-primary-key read.
    private MatchResult checkOneHop(String repoType, String loadMethod, String entity, String entityKey,
                                    Type idType, String idFieldName) {
        Optional<ClassOrInterfaceDeclaration> iface = catalog.declaration(repoType);
        if (iface.isEmpty() || !iface.get().isInterface()) {
            return MatchResult.nearMiss("the load target '" + repoType
                    + "' is neither a Spring Data repository of " + entity
                    + " nor a hand-written repository interface (condition 4)");
        }
        Optional<MethodDeclaration> ifaceMethod = singleParamMethod(iface.get(), loadMethod);
        if (ifaceMethod.isEmpty() || !returnsEntityOrOptional(ifaceMethod.get(), entity)
                || !singleParamMapsTo(ifaceMethod.get(), idType)) {
            return MatchResult.nearMiss("repository method '" + repoType + "#" + loadMethod
                    + "' does not read " + entity + " by its id (condition 4b)");
        }
        List<ClassOrInterfaceDeclaration> impls = catalog.repositoryImplementationsOf(repoType);
        if (impls.size() != 1) {
            return MatchResult.nearMiss("'" + repoType + "' does not have exactly one @Repository "
                    + "implementation in the scanned source (condition 4b)");
        }
        ClassOrInterfaceDeclaration impl = impls.get(0);
        Optional<MethodDeclaration> implMethod = singleParamMethod(impl, loadMethod);
        if (implMethod.isEmpty() || implMethod.get().getBody().isEmpty()) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' has no analyzable body (condition 4b)");
        }
        return checkDelegationBody(implMethod.get(), impl, repoType, loadMethod, entity, entityKey,
                idFieldName);
    }

    // The one-hop implementation body must be exactly: return <springDataField>.<pkRead>(<param>);
    private MatchResult checkDelegationBody(MethodDeclaration implMethod, ClassOrInterfaceDeclaration impl,
                                            String repoType, String loadMethod, String entity,
                                            String entityKey, String idFieldName) {
        List<Statement> statements = implMethod.getBody().orElseThrow().getStatements();
        String implParam = implMethod.getParameter(0).getNameAsString();
        if (statements.size() != 1 || !statements.get(0).isReturnStmt()) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' does not delegate in a single return statement (condition 4b)");
        }
        Optional<Expression> returned = statements.get(0).asReturnStmt().getExpression();
        if (returned.isEmpty() || !returned.get().isMethodCallExpr()) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' does not delegate to a repository call (condition 4b)");
        }
        MethodCallExpr delegate = returned.get().asMethodCallExpr();
        Map<String, String> implRepoFields = repositoryFields(impl);
        String delegateField = receiverFieldName(delegate);
        String delegateRepoType = delegateField == null ? null : implRepoFields.get(delegateField);
        if (delegateRepoType == null) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' does not delegate to a repository field (condition 4b)");
        }
        if (!PK_READS.contains(delegate.getNameAsString())) {
            // P1b (section 4.2.10): the one-hop delegate terminal may be a custom @Query, verified as a
            // by-primary-key select. A refusal (or a non-@Query delegate) is terminal; a verified query
            // falls through to the same entity-of and verbatim-forward checks a framework read faces.
            MatchResult p1b = p1bDelegate(delegateRepoType, delegate.getNameAsString(), entity,
                    idFieldName, repoType, loadMethod);
            if (p1b != null) {
                return p1b;
            }
        }
        Optional<SpringDataRepo> sd = catalog.springDataRepo(delegateRepoType);
        if (sd.isEmpty() || !isRepositoryOf(sd.get(), entityKey)) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' delegates to '" + delegateRepoType + "', which is not a Spring Data repository of "
                    + entity + " (condition 4b)");
        }
        if (!isVerbatimArgument(delegate, implParam)) {
            return MatchResult.nearMiss("'" + impl.getNameAsString() + "#" + loadMethod
                    + "' does not forward its parameter verbatim to the delegate (condition 4b, 5)");
        }
        return MatchResult.match(null, "loads " + entity + " by primary key through " + repoType + "#"
                + loadMethod + ", whose implementation " + impl.getNameAsString() + " delegates one hop to "
                + delegateRepoType + "#" + delegate.getNameAsString() + ", " + delegateRepoType
                + " extends a Spring Data repository of " + entity);
    }

    // Conditions 7, 8, 9: the body is one of the two read shapes, with exactly one mapping step whose
    // sole argument is the loaded entity, and the mapping callee resolves in-tree to (E) -> view.
    private MatchResult checkShapeAndMapping(BlockStmt body, MethodCallExpr load,
                                             ClassOrInterfaceDeclaration service, String entity, String view,
                                             String loadExplanation) {
        List<Statement> statements = body.getStatements();

        // Fused (7a): a single return. The load is either wrapped by a mapping call whose sole
        // argument is the unwrapped load (F1), or transformed by an Optional.map(methodReference) (F3).
        if (statements.size() == 1 && statements.get(0).isReturnStmt()) {
            Optional<MethodCallExpr> mapCall = enclosingOptionalMap(load);
            if (mapCall.isPresent()) {
                Expression mapArg = mapCall.get().getArgument(0);
                if (!mapArg.isMethodReferenceExpr()) {
                    return MatchResult.nearMiss("Optional.map takes something other than a method "
                            + "reference (condition 8)");
                }
                return resolveMethodReference(mapArg.asMethodReferenceExpr(), service, entity, view,
                        loadExplanation);
            }
            Expression unwrapped = unwrappedLoadValue(load);
            Optional<Expression> mappingCall = enclosingMappingCall(unwrapped);
            if (mappingCall.isEmpty()) {
                return MatchResult.nearMiss("the fused return is not a single mapping call over the "
                        + "loaded entity (conditions 7, 8)");
            }
            return resolveMappingCall(mappingCall.get(), unwrapped, service, entity, view, loadExplanation);
        }

        // Two-statement (7b): a local initialized from the unwrapped load, then return MAP(local).
        if (statements.size() == 2) {
            Optional<String> local = localFromLoad(statements.get(0), load);
            if (local.isEmpty()) {
                return MatchResult.nearMiss("the first statement is not a local initialized from the "
                        + "load (condition 7b)");
            }
            if (!statements.get(1).isReturnStmt()) {
                return MatchResult.nearMiss("the second statement is not a return (condition 7b)");
            }
            Optional<Expression> returned = statements.get(1).asReturnStmt().getExpression();
            if (returned.isEmpty()) {
                return MatchResult.nearMiss("the return has no expression (condition 7b)");
            }
            return resolveMappingCallOverLocal(returned.get(), local.get(), service, entity, view,
                    loadExplanation);
        }

        return MatchResult.nearMiss("the body is not one of the two permitted read shapes (condition 7)");
    }

    private MatchResult resolveMappingCallOverLocal(Expression returned, String local,
                                                    ClassOrInterfaceDeclaration service, String entity,
                                                    String view, String loadExplanation) {
        Optional<Expression> soleArg = singleArgumentOf(returned);
        if (soleArg.isEmpty() || !isNameExpr(soleArg.get(), local)) {
            return MatchResult.nearMiss("the mapping step's sole argument is not the loaded entity '"
                    + local + "' verbatim; a navigation or extraction disqualifies (condition 8)");
        }
        return resolveMappingCall(returned, soleArg.get(), service, entity, view, loadExplanation);
    }

    // Condition 9: resolve the mapping callee to an in-tree declaration taking exactly E and
    // returning the view. An unresolvable callee refuses.
    private MatchResult resolveMappingCall(Expression mappingCall, Expression loadedValue,
                                           ClassOrInterfaceDeclaration service, String entity, String view,
                                           String loadExplanation) {
        if (mappingCall.isObjectCreationExpr()) {
            String created = mappingCall.asObjectCreationExpr().getType().getNameAsString();
            return resolveInType(created, created, entity, view, loadExplanation, created + "(new)");
        }
        if (!mappingCall.isMethodCallExpr()) {
            return MatchResult.nearMiss("the mapping step is neither a method call nor a constructor "
                    + "(condition 8)");
        }
        MethodCallExpr call = mappingCall.asMethodCallExpr();
        // Confirm the loaded value is exactly the sole argument (fused-i and 7b share this check).
        if (call.getArguments().size() != 1 || call.getArgument(0) != loadedValue) {
            return MatchResult.nearMiss("the mapping step's sole argument is not the loaded entity "
                    + "verbatim (condition 8)");
        }
        String mapperType = mappingCall.asMethodCallExpr().getScope()
                .map(scope -> mapperTypeOfScope(scope, service)).orElse(null);
        if (mapperType == null) {
            return MatchResult.nearMiss("the mapping callee's receiver does not resolve to an in-tree "
                    + "type (condition 9)");
        }
        return resolveInType(mapperType, call.getNameAsString(), entity, view, loadExplanation,
                mapperType + "#" + call.getNameAsString());
    }

    private MatchResult resolveMethodReference(MethodReferenceExpr ref, ClassOrInterfaceDeclaration service,
                                               String entity, String view, String loadExplanation) {
        String mapperType = mapperTypeOfScope(ref.getScope(), service);
        if (mapperType == null) {
            return MatchResult.nearMiss("the method reference's receiver does not resolve to an in-tree "
                    + "type (condition 9)");
        }
        return resolveInType(mapperType, ref.getIdentifier(), entity, view, loadExplanation,
                mapperType + "::" + ref.getIdentifier());
    }

    // Find, on the mapper declaration, a callable named `member` taking exactly E and returning the
    // view. Constructors are matched when the type itself is the view.
    private MatchResult resolveInType(String typeSimpleName, String member, String entity, String view,
                                      String loadExplanation, String calleeLabel) {
        Optional<ClassOrInterfaceDeclaration> decl = catalog.declaration(typeSimpleName);
        if (decl.isEmpty()) {
            return MatchResult.nearMiss("the mapping callee '" + calleeLabel
                    + "' does not resolve to an in-tree declaration (condition 9)");
        }
        if (typeSimpleName.equals(member)) {
            boolean ctorMatches = decl.get().getConstructors().stream()
                    .anyMatch(c -> c.getParameters().size() == 1
                            && lastSegment(c.getParameter(0).getType().asString()).equals(entity));
            if (ctorMatches && typeSimpleName.equals(view)) {
                return MatchResult.match(view, explanation(calleeLabel, entity, view, loadExplanation));
            }
            return MatchResult.nearMiss("no in-tree constructor '" + calleeLabel + "(" + entity
                    + ")' produces the view '" + view + "' (condition 9)");
        }
        boolean methodMatches = decl.get().getMethods().stream()
                .filter(m -> m.getNameAsString().equals(member))
                .anyMatch(m -> m.getParameters().size() == 1
                        && lastSegment(m.getParameter(0).getType().asString()).equals(entity)
                        && lastSegment(m.getType().asString()).equals(view));
        if (!methodMatches) {
            return MatchResult.nearMiss("the mapping callee '" + calleeLabel + "' has no in-tree "
                    + "declaration taking (" + entity + ") and returning '" + view + "' (condition 9)");
        }
        return MatchResult.match(view, explanation(calleeLabel, entity, view, loadExplanation));
    }

    private static String explanation(String calleeLabel, String entity, String view, String loadExplanation) {
        return loadExplanation + "; maps to the view via " + calleeLabel + "(" + entity + ") : " + view
                + "; output stays ENTITY_REF(" + entity + ") with the view recorded";
    }

    // The unwrapped load value: the load followed by a chain of Optional unwrapping (orElseThrow,
    // orElse, get). The topmost node of that chain is the entity value.
    private static Expression unwrappedLoadValue(MethodCallExpr load) {
        Expression current = load;
        while (true) {
            MethodCallExpr parent = enclosingCallOn(current);
            if (parent == null || !UNWRAP.contains(parent.getNameAsString())) {
                return current;
            }
            current = parent;
        }
    }

    // An Optional.map(...) call directly enclosing the load (fused ii), if present.
    private static Optional<MethodCallExpr> enclosingOptionalMap(MethodCallExpr load) {
        Expression current = load;
        while (true) {
            MethodCallExpr parent = enclosingCallOn(current);
            if (parent == null) {
                return Optional.empty();
            }
            if (parent.getNameAsString().equals("map") && parent.getArguments().size() == 1) {
                return Optional.of(parent);
            }
            if (!UNWRAP.contains(parent.getNameAsString())) {
                return Optional.empty();
            }
            current = parent;
        }
    }

    // The method call, if any, that has `node` as its receiver, for example `node.orElseThrow()`.
    private static MethodCallExpr enclosingCallOn(Expression node) {
        if (node.getParentNode().isPresent()
                && node.getParentNode().get() instanceof MethodCallExpr parent
                && parent.getScope().isPresent() && parent.getScope().get() == node) {
            return parent;
        }
        return null;
    }

    // The call or constructor whose sole argument is the unwrapped load value (fused i).
    private static Optional<Expression> enclosingMappingCall(Expression unwrapped) {
        return unwrapped.getParentNode()
                .filter(p -> p instanceof MethodCallExpr || p instanceof ObjectCreationExpr)
                .map(p -> (Expression) p)
                .filter(p -> singleArgumentOf(p).map(a -> a == unwrapped).orElse(false));
    }

    private static Optional<String> localFromLoad(Statement statement, MethodCallExpr load) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }
        Expression expr = statement.asExpressionStmt().getExpression();
        if (!(expr instanceof VariableDeclarationExpr decl) || decl.getVariables().size() != 1) {
            return Optional.empty();
        }
        VariableDeclarator var = decl.getVariable(0);
        Optional<Expression> init = var.getInitializer();
        if (init.isEmpty()) {
            return Optional.empty();
        }
        // The initializer must be the load, possibly through an unwrap chain.
        if (unwrappedLoadValue(load) != init.get() && load != init.get()) {
            return Optional.empty();
        }
        return Optional.of(var.getNameAsString());
    }

    private static Optional<Expression> singleArgumentOf(Expression call) {
        if (call instanceof MethodCallExpr m && m.getArguments().size() == 1) {
            return Optional.of(m.getArgument(0));
        }
        if (call instanceof ObjectCreationExpr o && o.getArguments().size() == 1) {
            return Optional.of(o.getArgument(0));
        }
        return Optional.empty();
    }

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

    private static List<MethodCallExpr> repositoryCalls(BlockStmt body, Set<String> repoFieldNames) {
        List<MethodCallExpr> calls = new ArrayList<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(call);
            if (field != null && repoFieldNames.contains(field)) {
                calls.add(call);
            }
        }
        return calls;
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

    // The declared type of the mapper the call is invoked on: a service field's type (instance
    // method or method reference on a field), or a type name used statically.
    private String mapperTypeOfScope(Expression scope, ClassOrInterfaceDeclaration service) {
        if (scope.isNameExpr()) {
            String name = scope.asNameExpr().getNameAsString();
            String fieldType = fieldType(service, name);
            return fieldType != null ? fieldType : name;
        }
        if (scope.isFieldAccessExpr() && scope.asFieldAccessExpr().getScope().isThisExpr()) {
            String name = scope.asFieldAccessExpr().getNameAsString();
            return fieldType(service, name);
        }
        if (scope.isTypeExpr()) {
            // A method reference like `mapper::toVto` parses its receiver as a type expression even
            // when `mapper` is a field; resolve it as a field first, then as a static type.
            String name = lastSegment(scope.asTypeExpr().getType().asString());
            String fieldType = fieldType(service, name);
            return fieldType != null ? fieldType : name;
        }
        return null;
    }

    private static String fieldType(ClassOrInterfaceDeclaration type, String fieldName) {
        for (FieldDeclaration declaration : type.getFields()) {
            for (VariableDeclarator variable : declaration.getVariables()) {
                if (variable.getNameAsString().equals(fieldName)) {
                    return lastSegment(declaration.getElementType().asString());
                }
            }
        }
        return null;
    }

    private static boolean isVerbatimArgument(MethodCallExpr call, String param) {
        return call.getArguments().size() == 1 && isNameExpr(call.getArgument(0), param);
    }

    private static boolean isNameExpr(Expression expr, String name) {
        return expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals(name);
    }

    private static Optional<MethodDeclaration> singleParamMethod(ClassOrInterfaceDeclaration type, String name) {
        return type.getMethods().stream()
                .filter(m -> m.getNameAsString().equals(name) && m.getParameters().size() == 1)
                .findFirst();
    }

    private boolean singleParamMapsTo(MethodDeclaration method, Type idType) {
        Optional<Type> mapped = typeMapper.map(method.getParameter(0).getType().asString());
        return mapped.isPresent() && mapped.get().equals(idType);
    }

    private boolean idTypeMatches(String idTypeAsWritten, Type idType) {
        Optional<Type> mapped = typeMapper.map(idTypeAsWritten);
        return mapped.isPresent() && mapped.get().equals(idType);
    }

    private static boolean returnsEntityOrOptional(MethodDeclaration method, String entity) {
        String returnType = method.getType().asString();
        String simple = lastSegment(stripGenerics(returnType));
        if (simple.equals(entity)) {
            return true;
        }
        if (simple.equals("Optional")) {
            int open = returnType.indexOf('<');
            int close = returnType.lastIndexOf('>');
            if (open >= 0 && close > open) {
                return lastSegment(returnType.substring(open + 1, close).trim()).equals(entity);
            }
        }
        return false;
    }

    private static String stripGenerics(String type) {
        int open = type.indexOf('<');
        return open >= 0 ? type.substring(0, open) : type;
    }

    private static String lastSegment(String name) {
        String trimmed = name.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot >= 0 ? trimmed.substring(dot + 1) : trimmed;
    }
}
