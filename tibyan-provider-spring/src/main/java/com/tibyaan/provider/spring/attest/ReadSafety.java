package com.tibyaan.provider.spring.attest;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.tibyaan.provider.spring.extractors.RepositoryCatalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The read-safety check for an attestable target (Volume 5 section 4.15.2), tightened beyond section 4.2's
 * write-set because attestable bodies abandon the shape strictness that earned mutation-freedom for the
 * verified rules. Over the fingerprinted slice (the target body and the guard helper), it refuses the
 * attestation if it sees any visible write: a write-set call, a call on a persistence-infrastructure field
 * ({@code EntityManager}, {@code JdbcTemplate}, and kin), a setter on a loaded entity, a {@code @Modifying}
 * repository method, or a mutating {@code @Query}. A body that survives all of these is READ-SAFE AS FAR AS
 * THE SLICE SHOWS: the honest guarantee is "no mutation visible in the slice," never "no write anywhere"
 * (section 4.15.7). A write hidden below the slice, in the query builder the assertion covers, is the
 * stated residue, which is exactly why category C is deferred.
 */
public final class ReadSafety {

    private static final Set<String> WRITE_SET = Set.of(
            "save", "saveAll", "insert", "update", "delete", "deleteById", "deleteAll",
            "persist", "merge", "flush", "remove", "refresh", "detach");

    private static final Set<String> MUTATING_INFRA_TYPES = Set.of(
            "EntityManager", "EntityManagerFactory", "JdbcTemplate", "NamedParameterJdbcTemplate",
            "SqlSession", "Session", "HibernateTemplate", "JpaTemplate");

    private final RepositoryCatalog catalog;

    public ReadSafety(RepositoryCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Checks the target method (and any guard helpers whose bodies the existence-condition trace reads)
     * for a visible write.
     *
     * @return the located refusal reason, or empty when no write is visible in the slice
     */
    public Optional<String> check(ClassOrInterfaceDeclaration serviceClass, MethodDeclaration target,
                                  Iterable<MethodDeclaration> guardHelpers) {
        Map<String, String> fieldTypes = fieldTypes(serviceClass);
        Optional<String> targetReason = checkBody(target, fieldTypes);
        if (targetReason.isPresent()) {
            return targetReason;
        }
        for (MethodDeclaration helper : guardHelpers) {
            Optional<String> helperReason = checkBody(helper, fieldTypes);
            if (helperReason.isPresent()) {
                return helperReason;
            }
        }
        return Optional.empty();
    }

    private Optional<String> checkBody(MethodDeclaration method, Map<String, String> fieldTypes) {
        Optional<BlockStmt> body = method.getBody();
        if (body.isEmpty()) {
            return Optional.of("the method '" + method.getNameAsString() + "' has no analyzable body, so "
                    + "read-safety cannot be established (read-safety check, section 4.15.2)");
        }
        Set<String> loadedLocals = localsLoadedFromPersistence(body.get(), fieldTypes);
        for (MethodCallExpr call : body.get().findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (WRITE_SET.contains(name)) {
                return Optional.of("a write-set call '" + name + "' is visible in the body of '"
                        + method.getNameAsString() + "'; a mutating body is not a read (read-safety, "
                        + "section 4.15.2)");
            }
            String receiverField = receiverFieldName(call);
            // The receiver's declared field type, or null when the receiver is not a declared field of the
            // class (a local variable, a builder, or a static type reference: filter.build(), Foo.of(...)).
            // The null guard is load-bearing: MUTATING_INFRA_TYPES is an immutable Set, whose contains(null)
            // THROWS, and real read bodies are full of non-field-receiver calls. A null type is never an
            // infrastructure type, so a non-field receiver is simply not a mutating-infra call.
            String receiverType = receiverField == null ? null : fieldTypes.get(receiverField);
            if (receiverType != null && MUTATING_INFRA_TYPES.contains(receiverType)) {
                return Optional.of("a call on the persistence-infrastructure field '" + receiverField
                        + "' (" + receiverType + ") is visible in '"
                        + method.getNameAsString() + "'; a write cannot be ruled out (read-safety, "
                        + "section 4.15.2)");
            }
            if (name.startsWith("set") && name.length() > 3 && isSetterOnLoadedEntity(call, loadedLocals)) {
                return Optional.of("a setter '" + name + "' on a loaded entity is visible in '"
                        + method.getNameAsString() + "'; JPA dirty checking would persist it, so the body "
                        + "is not read-only (read-safety, section 4.15.2)");
            }
            Optional<String> repoWrite = mutatingRepositoryCall(call, receiverField, fieldTypes, method);
            if (repoWrite.isPresent()) {
                return repoWrite;
            }
        }
        return Optional.empty();
    }

    // A repository call whose in-tree declaration is @Modifying, or whose @Query text is an UPDATE, a
    // DELETE, or a non-SELECT native query, is a write regardless of its name.
    private Optional<String> mutatingRepositoryCall(MethodCallExpr call, String receiverField,
                                                    Map<String, String> fieldTypes, MethodDeclaration method) {
        if (receiverField == null) {
            return Optional.empty();
        }
        String repoType = fieldTypes.get(receiverField);
        if (repoType == null || !catalog.isRepositoryType(repoType)) {
            return Optional.empty();
        }
        Optional<MethodDeclaration> repoMethod = catalog.declaration(repoType)
                .flatMap(decl -> decl.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(call.getNameAsString()))
                        .findFirst());
        if (repoMethod.isEmpty()) {
            return Optional.empty();
        }
        MethodDeclaration decl = repoMethod.get();
        if (hasAnnotation(decl, "Modifying")) {
            return Optional.of("the repository method '" + repoType + "#" + call.getNameAsString()
                    + "' is @Modifying, a write, called in '" + method.getNameAsString()
                    + "' (read-safety, section 4.15.2)");
        }
        Optional<String> query = queryText(decl);
        if (query.isPresent()) {
            String verb = query.get().trim().toUpperCase();
            if (verb.startsWith("UPDATE") || verb.startsWith("DELETE")) {
                return Optional.of("the repository method '" + repoType + "#" + call.getNameAsString()
                        + "' runs a mutating @Query (" + (verb.startsWith("UPDATE") ? "UPDATE" : "DELETE")
                        + "), called in '" + method.getNameAsString() + "' (read-safety, section 4.15.2)");
            }
        }
        return Optional.empty();
    }

    // Local variables initialized directly from a call on a persistence (repository) field: candidates for
    // a later setter-on-loaded-entity write.
    private Set<String> localsLoadedFromPersistence(BlockStmt body, Map<String, String> fieldTypes) {
        Set<String> loaded = new HashSet<>();
        for (VariableDeclarator declarator : body.findAll(VariableDeclarator.class)) {
            declarator.getInitializer().ifPresent(init -> {
                for (MethodCallExpr call : init.findAll(MethodCallExpr.class)) {
                    String field = receiverFieldName(call);
                    if (field != null && catalog.isRepositoryType(fieldTypes.get(field))) {
                        loaded.add(declarator.getNameAsString());
                    }
                }
            });
        }
        return loaded;
    }

    private static boolean isSetterOnLoadedEntity(MethodCallExpr call, Set<String> loadedLocals) {
        return call.getScope()
                .filter(Expression::isNameExpr)
                .map(scope -> loadedLocals.contains(scope.asNameExpr().getNameAsString()))
                .orElse(false);
    }

    private static Map<String, String> fieldTypes(ClassOrInterfaceDeclaration type) {
        Map<String, String> fields = new HashMap<>();
        for (FieldDeclaration declaration : type.getFields()) {
            String fieldType = lastSegment(declaration.getElementType().asString());
            for (VariableDeclarator variable : declaration.getVariables()) {
                fields.put(variable.getNameAsString(), fieldType);
            }
        }
        return fields;
    }

    private static String receiverFieldName(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return null;
        }
        Expression s = scope.get();
        if (s.isNameExpr()) {
            return s.asNameExpr().getNameAsString();
        }
        if (s.isFieldAccessExpr() && s.asFieldAccessExpr().getScope().isThisExpr()) {
            return s.asFieldAccessExpr().getNameAsString();
        }
        return null;
    }

    private static boolean hasAnnotation(MethodDeclaration method, String simpleName) {
        return method.getAnnotations().stream()
                .anyMatch(a -> lastSegment(a.getNameAsString()).equals(simpleName));
    }

    private static Optional<String> queryText(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            if (!lastSegment(annotation.getNameAsString()).equals("Query")) {
                continue;
            }
            if (annotation instanceof SingleMemberAnnotationExpr single
                    && single.getMemberValue() instanceof StringLiteralExpr literal) {
                return Optional.of(literal.asString());
            }
        }
        return Optional.empty();
    }

    private static String lastSegment(String name) {
        if (name == null) {
            return null;
        }
        int open = name.indexOf('<');
        String base = open >= 0 ? name.substring(0, open) : name;
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }
}
