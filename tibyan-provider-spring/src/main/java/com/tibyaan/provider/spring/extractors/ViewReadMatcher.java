package com.tibyaan.provider.spring.extractors;

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

    public ViewReadMatcher(RepositoryCatalog catalog, JavaTypeMapper typeMapper) {
        this.catalog = catalog;
        this.typeMapper = typeMapper;
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
            return MatchResult.nearMiss("body contains no persistence access, so there is no load to "
                    + "verify (condition 4); a delegation-only facade is out of scope");
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
        MatchResult loadEvidence = checkByPrimaryKeyLoad(load, repoType, entity, intent.idType());
        if (!loadEvidence.matched()) {
            return loadEvidence;
        }

        return checkShapeAndMapping(body, load, service, entity, view, loadEvidence.explanation());
    }

    // Condition 4: the load is a by-primary-key read of E, direct (4a) or one-hop (4b).
    private MatchResult checkByPrimaryKeyLoad(MethodCallExpr load, String repoType, String entity, Type idType) {
        String loadMethod = load.getNameAsString();
        Optional<SpringDataRepo> direct = catalog.springDataRepo(repoType);
        if (direct.isPresent()) {
            SpringDataRepo repo = direct.get();
            if (!repo.entitySimpleName().equals(entity)) {
                return MatchResult.nearMiss("the load is a by-primary-key read of '"
                        + repo.entitySimpleName() + "', not '" + entity + "' (condition 4)");
            }
            if (!idTypeMatches(repo.idTypeAsWritten(), idType)) {
                return MatchResult.nearMiss("the repository '" + repoType + "' has id type '"
                        + repo.idTypeAsWritten() + "', which does not match the entity's id (condition 4)");
            }
            if (!PK_READS.contains(loadMethod)) {
                return MatchResult.nearMiss("'" + loadMethod + "' on '" + repoType
                        + "' is not a framework by-primary-key read (condition 4)");
            }
            return MatchResult.match(null, "loads " + entity + " by primary key directly through "
                    + repoType + "#" + loadMethod + ", " + repoType + " extends a Spring Data repository of "
                    + entity + " (" + repo.idTypeAsWritten() + ")");
        }
        return checkOneHop(repoType, loadMethod, entity, idType);
    }

    // Condition 4b: the load calls a hand-written repository method whose single @Repository
    // implementation delegates in exactly one return statement to a Spring Data by-primary-key read.
    private MatchResult checkOneHop(String repoType, String loadMethod, String entity, Type idType) {
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
        return checkDelegationBody(implMethod.get(), impl, repoType, loadMethod, entity);
    }

    // The one-hop implementation body must be exactly: return <springDataField>.<pkRead>(<param>);
    private MatchResult checkDelegationBody(MethodDeclaration implMethod, ClassOrInterfaceDeclaration impl,
                                            String repoType, String loadMethod, String entity) {
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
            return MatchResult.nearMiss("the load resolves through " + repoType + "#" + loadMethod
                    + ", whose implementation delegates to " + delegateRepoType + "#"
                    + delegate.getNameAsString() + ", not a framework by-primary-key read; "
                    + "by-primary-key evidence unavailable, deferred pattern P1b");
        }
        Optional<SpringDataRepo> sd = catalog.springDataRepo(delegateRepoType);
        if (sd.isEmpty() || !sd.get().entitySimpleName().equals(entity)) {
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
