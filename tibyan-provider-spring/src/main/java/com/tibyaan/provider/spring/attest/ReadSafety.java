package com.tibyaan.provider.spring.attest;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import com.tibyaan.provider.spring.extractors.RepositoryCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 *
 * <p>THE WRITE-SET CLAUSE IS RECEIVER-AWARE (Volume 5 section 4.15.2.1, ratified 2026-08-04). A mutating
 * NAME alone is not a write: {@code counts.merge(...)} on a map the method itself constructed mutates no
 * application state, while the same name on a repository, an {@code EntityManager}, or any field does. The
 * clause therefore refuses a write-set call UNLESS its receiver is PROVABLY local and non-persistent by all
 * six conditions L1 to L6, and refusal remains the default for every other receiver, including every one
 * the tool cannot decide. The carve-out does NOT widen the guarantee: what it removes is a false refusal,
 * not a claim. Its governing bias is the MIS-SAFE invariant: a false refusal of a genuinely safe local
 * aggregation is an acceptable coverage miss, a false pass of a persistence write is a safety breach.
 */
public final class ReadSafety {

    private static final Set<String> WRITE_SET = Set.of(
            "save", "saveAll", "insert", "update", "delete", "deleteById", "deleteAll",
            "persist", "merge", "flush", "remove", "refresh", "detach");

    private static final Set<String> MUTATING_INFRA_TYPES = Set.of(
            "EntityManager", "EntityManagerFactory", "JdbcTemplate", "NamedParameterJdbcTemplate",
            "SqlSession", "Session", "HibernateTemplate", "JpaTemplate");

    // The CLOSED allowlist of JDK mutable containers a write-set call may be permitted on, when every
    // other provable-local condition holds (Volume 5 section 4.15.2.1, condition L4). Extending this set
    // is a design change, not a build decision.
    private static final Set<String> LOCAL_CONTAINER_ALLOWLIST = Set.of(
            "HashMap", "LinkedHashMap", "TreeMap", "ConcurrentHashMap", "EnumMap", "IdentityHashMap",
            "HashSet", "LinkedHashSet", "TreeSet", "ArrayList", "LinkedList", "ArrayDeque",
            "PriorityQueue", "StringBuilder", "StringBuffer");

    // The only packages an allowlisted container's simple name may be imported from (condition L4's
    // no-shadow guard). An import of the same simple name from anywhere else defeats the proof.
    private static final Set<String> ALLOWED_CONTAINER_PACKAGES =
            Set.of("java.util", "java.util.concurrent", "java.lang");

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
            // The write-set clause is RECEIVER-AWARE (Volume 5 section 4.15.2.1). A mutating NAME is a
            // write to application state UNLESS its receiver is PROVABLY a local, freshly constructed,
            // allowlisted container that never escapes to persistence. Refusal is the default: every
            // ambiguous or untraceable receiver refuses exactly as before the refinement.
            if (WRITE_SET.contains(name)) {
                Optional<String> writeRefusal =
                        writeSetRefusal(call, name, method, body.get(), fieldTypes, loadedLocals);
                if (writeRefusal.isPresent()) {
                    return writeRefusal;
                }
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

    /**
     * Classifies the receiver of a write-set call (Volume 5 section 4.15.2.1). Returns the located refusal
     * reason, or empty when and only when the receiver is PROVABLY local and non-persistent, in which case
     * the call is not a write to application state and read-safety does not refuse on it.
     *
     * <p>The bias is absolute and one-directional: a false refusal of a genuinely safe local aggregation is
     * a coverage miss, a false pass of a persistence write is a safety breach (the MIS-SAFE invariant), so
     * every case the tool cannot decide from the written AST refuses.
     */
    private Optional<String> writeSetRefusal(MethodCallExpr call, String name, MethodDeclaration method,
                                             BlockStmt body, Map<String, String> fieldTypes,
                                             Set<String> loadedLocals) {
        String where = " in the body of '" + method.getNameAsString() + "' (read-safety, section 4.15.2.1";
        Optional<Expression> scope = call.getScope();
        // R9: an unqualified write-set call is a call on the enclosing type itself, never a local.
        if (scope.isEmpty()) {
            return Optional.of("a write-set call '" + name + "' has no receiver, so it is a call on the "
                    + "enclosing type" + where + ", R9)");
        }
        // R1, R2, R3: a receiver that is a declared FIELD is never local, whatever its type. A field
        // outlives the call; a fresh local does not, and that is the whole distinction.
        String receiverField = receiverFieldName(call);
        String fieldType = receiverField == null ? null : fieldTypes.get(receiverField);
        if (fieldType != null) {
            if (catalog.isRepositoryType(fieldType)) {
                return Optional.of("a write-set call '" + name + "' on the repository field '"
                        + receiverField + "' (" + fieldType + ") is visible" + where + ", R1)");
            }
            if (MUTATING_INFRA_TYPES.contains(fieldType)) {
                return Optional.of("a write-set call '" + name + "' on the persistence-infrastructure field '"
                        + receiverField + "' (" + fieldType + ") is visible" + where + ", R2)");
            }
            return Optional.of("a write-set call '" + name + "' on the field '" + receiverField + "' ("
                    + fieldType + ") is visible; a field receiver is never a provable local, and a write "
                    + "through it cannot be ruled out" + where + ", R3)");
        }
        // L1: the local proof is available only for a bare simple name.
        if (!scope.get().isNameExpr()) {
            return Optional.of("a write-set call '" + name + "' has a receiver expression that is not a bare "
                    + "simple name, so its origin is not directly traceable" + where + ", L1, R9)");
        }
        String local = scope.get().asNameExpr().getNameAsString();
        // R4: a method parameter is not a local the method constructed.
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getNameAsString().equals(local)) {
                return Optional.of("a write-set call '" + name + "' on the method parameter '" + local
                        + "' is visible; a parameter's origin is outside the method" + where + ", R4)");
            }
        }
        // L2: exactly one declarator for the name, declared by a statement of this method's own body and
        // not nested in a lambda. A for-each variable, a for-init variable, a catch parameter, and a lambda
        // parameter all produce no qualifying declarator and so refuse here.
        List<VariableDeclarator> declarators = new ArrayList<>();
        for (VariableDeclarator declarator : body.findAll(VariableDeclarator.class)) {
            if (declarator.getNameAsString().equals(local) && isPlainLocalDeclaration(declarator)) {
                declarators.add(declarator);
            }
        }
        if (declarators.size() != 1) {
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '" + local
                    + "' does not resolve to exactly one plain local declaration in this body"
                    + where + ", L2)");
        }
        VariableDeclarator declarator = declarators.get(0);
        Optional<Expression> initializer = declarator.getInitializer();
        // L3, with R4 and R5 given their own messages because they are the cases the guarantee most
        // depends on: an alias of a parameter, and a collection handed back by persistence.
        if (initializer.isEmpty() || !initializer.get().isObjectCreationExpr()) {
            if (initializer.isPresent() && initializer.get().isNameExpr()) {
                return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '"
                        + local + "' is an alias of '" + initializer.get().asNameExpr().getNameAsString()
                        + "', not a fresh construction" + where + ", R4)");
            }
            if (initializer.isPresent() && initializer.get().isMethodCallExpr()
                    && originatesFromPersistence(initializer.get().asMethodCallExpr(), fieldTypes,
                            loadedLocals)) {
                return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '"
                        + local + "' was obtained from a persistence access, so it may be a managed "
                        + "collection" + where + ", R5)");
            }
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '" + local
                    + "' is not initialized by an in-method 'new' construction, so its origin is not "
                    + "structurally proven" + where + ", L3)");
        }
        // L4: the constructed type is an allowlisted JDK container, unshadowed in-tree and unimported
        // from anywhere unexpected.
        String created = lastSegment(initializer.get().asObjectCreationExpr().getType().asString());
        if (!LOCAL_CONTAINER_ALLOWLIST.contains(created)) {
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and its "
                    + "constructed type '" + created + "' is not an allowlisted local container"
                    + where + ", L4, R6)");
        }
        if (catalog.declaration(created).isPresent()) {
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and its "
                    + "constructed type '" + created + "' is shadowed by an in-tree declaration of the "
                    + "same simple name" + where + ", L4, R6)");
        }
        Optional<String> badImport = unexpectedImportOf(method, created);
        if (badImport.isPresent()) {
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and its "
                    + "constructed type '" + created + "' is imported from '" + badImport.get()
                    + "', not from the JDK" + where + ", L4, R6)");
        }
        // L5: the declarator is the only assignment.
        for (AssignExpr assignment : body.findAll(AssignExpr.class)) {
            if (assignment.getTarget().isNameExpr()
                    && assignment.getTarget().asNameExpr().getNameAsString().equals(local)) {
                return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '"
                        + local + "' is reassigned in this body, so its origin is no longer the "
                        + "construction" + where + ", L5, R7)");
            }
        }
        // L6: the local never escapes to a persistence receiver, in either order.
        if (escapesToPersistence(body, local, fieldTypes)) {
            return Optional.of("a write-set call '" + name + "' on '" + local + "' is visible, and '" + local
                    + "' is passed to a persistence receiver in this body, so it may be managed"
                    + where + ", L6, R8)");
        }
        // Every condition held: the receiver is provably a local, freshly constructed, allowlisted
        // container that never escapes. The call is not a write to APPLICATION STATE. The guarantee is
        // unchanged: it still says only "no write to application state in what the tool read".
        return Optional.empty();
    }

    // A plain local declaration: a variable-declaration statement of the method's own body, not a for-each
    // variable, not a for-init variable, and not nested inside a lambda (condition L2).
    private static boolean isPlainLocalDeclaration(VariableDeclarator declarator) {
        Optional<Node> declarationExpr = declarator.getParentNode();
        if (declarationExpr.isEmpty() || !(declarationExpr.get() instanceof VariableDeclarationExpr)) {
            return false;
        }
        Optional<Node> statement = declarationExpr.get().getParentNode();
        if (statement.isEmpty() || !(statement.get() instanceof ExpressionStmt)) {
            return false;
        }
        Node cursor = statement.get();
        while (cursor.getParentNode().isPresent()) {
            cursor = cursor.getParentNode().get();
            if (cursor instanceof LambdaExpr) {
                return false;
            }
            if (cursor instanceof MethodDeclaration) {
                return true;
            }
        }
        return false;
    }

    // Whether an initializer call reaches persistence: a call on a repository or infrastructure field, or a
    // getter on a value that itself came from one (condition R5, the managed-collection case the guarantee
    // most depends on: entity.getItems() where entity was loaded through a repository).
    private boolean originatesFromPersistence(MethodCallExpr call, Map<String, String> fieldTypes,
                                              Set<String> loadedLocals) {
        for (MethodCallExpr inner : call.findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(inner);
            String type = field == null ? null : fieldTypes.get(field);
            if (type != null && (catalog.isRepositoryType(type) || MUTATING_INFRA_TYPES.contains(type))) {
                return true;
            }
            if (field != null && loadedLocals.contains(field)) {
                return true;
            }
        }
        return false;
    }

    // Condition L6: the name appears, at any depth, in the arguments of a call whose receiver is a
    // repository or persistence-infrastructure field. Order-insensitive by design.
    private boolean escapesToPersistence(BlockStmt body, String local, Map<String, String> fieldTypes) {
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            String field = receiverFieldName(call);
            String type = field == null ? null : fieldTypes.get(field);
            if (type == null || !(catalog.isRepositoryType(type) || MUTATING_INFRA_TYPES.contains(type))) {
                continue;
            }
            for (Expression argument : call.getArguments()) {
                for (NameExpr reference : argument.findAll(NameExpr.class)) {
                    if (reference.getNameAsString().equals(local)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Condition L4's import half: an import binding this simple name from outside the JDK packages the
    // allowlist belongs to.
    private static Optional<String> unexpectedImportOf(MethodDeclaration method, String simpleName) {
        Optional<CompilationUnit> unit = method.findCompilationUnit();
        if (unit.isEmpty()) {
            return Optional.empty();
        }
        for (ImportDeclaration importDeclaration : unit.get().getImports()) {
            if (importDeclaration.isAsterisk()) {
                continue;
            }
            String imported = importDeclaration.getNameAsString();
            if (!lastSegment(imported).equals(simpleName)) {
                continue;
            }
            int dot = imported.lastIndexOf('.');
            String packageName = dot >= 0 ? imported.substring(0, dot) : "";
            if (!ALLOWED_CONTAINER_PACKAGES.contains(packageName)) {
                return Optional.of(imported);
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
