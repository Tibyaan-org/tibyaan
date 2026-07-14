package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an entity's identifier, whether it declares one directly or inherits it (Volume 5,
 * section 4.13.2, the inherited-{@code @Id} eligibility rule).
 *
 * <p>An entity that declares its own single {@code @Id} resolves exactly as before. An entity that
 * declares none may still have one: standard JPA lets an entity inherit its identifier from a
 * {@code @MappedSuperclass} or from an {@code @Inheritance} parent. This resolver follows that chain
 * structurally, in the scanned source only, and refuses everything it cannot read.
 *
 * <p>The superclass is resolved by the SCOPING stage of the key-based resolver (Volume 5, section
 * 4.12.4, its Java name resolution to a single fully-qualified name) and then matched against ALL
 * in-tree class declarations, not against the entity-key set: a {@code @MappedSuperclass} is not an
 * {@code @Entity} and carries no key (section 4.12.2), so an entity-key match would wrongly miss it.
 *
 * <p>The walk covers the WHOLE in-tree chain rather than stopping at the first identifier, because
 * deciding that the identifier is unambiguous requires seeing every ancestor. It refuses, never
 * guesses: a superclass outside the scanned source, a composite inherited key, and an ambiguous
 * hierarchy (two ancestors each declaring an {@code @Id}) are all located refusals. It resolves an
 * identifier and nothing else: it grants no discovery, and the backing method is still found by
 * sections 4.1 and 4.2 over the entity, resolved by key, so it cannot bind anything.
 */
public final class IdentifierResolver {

    /** What the resolver found, or why it refused. */
    public enum Kind {
        /** A single usable identifier, declared directly or inherited. */
        FOUND,
        /** No identifier anywhere in the entity or its in-tree chain. */
        NONE,
        /** An {@code @EmbeddedId}, or more than one {@code @Id} on one class: a composite key. */
        COMPOSITE,
        /** Two different ancestors each declare an {@code @Id}: the inherited identifier is ambiguous. */
        AMBIGUOUS_HIERARCHY,
        /** The chain leaves the scanned source before an identifier is found: it cannot be read. */
        PARENT_NOT_IN_TREE
    }

    /**
     * @param kind             the outcome
     * @param idField          the single identifier field, when FOUND
     * @param declaringType    the simple name of the class declaring it, when FOUND
     * @param inherited        whether the identifier came from an ancestor rather than the entity
     * @param reason           the located refusal message, when not FOUND
     */
    public record Result(Kind kind, VariableDeclarator idField, String declaringType, boolean inherited,
                         String reason) {

        public boolean isFound() {
            return kind == Kind.FOUND;
        }
    }

    private static final String ID_ANNOTATION = "Id";
    private static final String EMBEDDED_ID_ANNOTATION = "EmbeddedId";
    private static final String TRANSIENT_ANNOTATION = "Transient";

    private final Map<String, ClassOrInterfaceDeclaration> classesByFqcn;
    private final TypeReferenceResolver resolver;

    private IdentifierResolver(Map<String, ClassOrInterfaceDeclaration> classesByFqcn,
                               TypeReferenceResolver resolver) {
        this.classesByFqcn = classesByFqcn;
        this.resolver = resolver;
    }

    /** Indexes every in-tree class declaration by its fully-qualified name (section 4.13.2, condition 2). */
    public static IdentifierResolver from(List<CompilationUnit> units, TypeReferenceResolver resolver) {
        Map<String, ClassOrInterfaceDeclaration> byFqcn = new HashMap<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface()) {
                    continue;
                }
                decl.getFullyQualifiedName().ifPresent(fqcn -> byFqcn.putIfAbsent(fqcn, decl));
            }
        }
        return new IdentifierResolver(byFqcn, resolver);
    }

    /** Resolves the entity's identifier (section 4.13.2). */
    public Result resolve(ClassOrInterfaceDeclaration entity) {
        String entityName = entity.getNameAsString();

        // Condition 1: an entity that declares its own identifier is decided exactly as before; the
        // inheritance walk is not consulted at all, so no existing outcome can move.
        List<VariableDeclarator> ownIds = idFields(entity);
        if (hasEmbeddedId(entity) || ownIds.size() > 1) {
            return new Result(Kind.COMPOSITE, null, entityName, false,
                    directCompositeReason(entity, entityName, ownIds));
        }
        if (ownIds.size() == 1) {
            return new Result(Kind.FOUND, ownIds.get(0), entityName, false, null);
        }
        return walkChain(entity, entityName);
    }

    // Conditions 2 to 4: walk the WHOLE in-tree superclass chain, collecting every identifier
    // declaration on every ancestor, then decide. The walk never stops at the first identifier.
    private Result walkChain(ClassOrInterfaceDeclaration entity, String entityName) {
        List<VariableDeclarator> found = new ArrayList<>();
        List<String> declaringAncestors = new ArrayList<>();
        List<String> compositeAncestors = new ArrayList<>();
        boolean embeddedId = false;
        String unreadableParent = null;
        String unreadableReason = null;

        ClassOrInterfaceDeclaration current = entity;
        Set<String> visited = new HashSet<>();
        while (true) {
            Optional<ClassOrInterfaceType> extended = superclassOf(current);
            if (extended.isEmpty()) {
                break;
            }
            String written = extended.get().getNameAsString();
            CompilationUnit unit = current.findCompilationUnit().orElse(null);
            if (unit == null) {
                unreadableParent = written;
                unreadableReason = "its declaration could not be located";
                break;
            }
            // Condition 2: the scoping stage of the key-based resolver, matched against ALL in-tree
            // class declarations. A @MappedSuperclass parent resolves here; an entity-key match would not.
            TypeReferenceResolver.Resolution resolution = resolver.resolve(written, unit);
            if (!resolution.isResolved()) {
                unreadableParent = written;
                unreadableReason = resolution.isAmbiguous()
                        ? "it does not resolve to a single in-tree class (candidates " + resolution.candidates() + ")"
                        : "it is not in the scanned source";
                break;
            }
            ClassOrInterfaceDeclaration parent = classesByFqcn.get(resolution.fqcn());
            if (parent == null) {
                unreadableParent = written;
                unreadableReason = "it is not in the scanned source";
                break;
            }
            if (!visited.add(resolution.fqcn())) {
                break;
            }

            List<VariableDeclarator> ids = idFields(parent);
            if (hasEmbeddedId(parent)) {
                embeddedId = true;
                compositeAncestors.add(parent.getNameAsString());
            }
            if (ids.size() > 1) {
                compositeAncestors.add(parent.getNameAsString());
            }
            if (!ids.isEmpty()) {
                found.addAll(ids);
                declaringAncestors.add(parent.getNameAsString());
            }
            current = parent;
        }

        // Condition 4: an @EmbeddedId, or more than one @Id on one ancestor, is a composite key.
        if (embeddedId || !compositeAncestors.isEmpty()) {
            return new Result(Kind.COMPOSITE, null, null, true,
                    inheritedCompositeReason(entityName, compositeAncestors, embeddedId));
        }
        // Condition 4: two different ancestors each declaring an @Id is an ambiguous inherited
        // identifier, a distinct refusal from a composite key. It is never resolved to the nearer one.
        if (declaringAncestors.size() > 1) {
            return new Result(Kind.AMBIGUOUS_HIERARCHY, null, null, true,
                    ambiguousHierarchyReason(entityName, declaringAncestors, found));
        }
        if (found.size() == 1) {
            return new Result(Kind.FOUND, found.get(0), declaringAncestors.get(0), true, null);
        }
        // No identifier anywhere. If the chain left the scanned source, say so: the tool never assumes
        // an unseen parent carries an @Id (condition 2).
        if (unreadableParent != null) {
            return new Result(Kind.PARENT_NOT_IN_TREE, null, null, true,
                    parentNotInTreeReason(entityName, unreadableParent, unreadableReason));
        }
        return new Result(Kind.NONE, null, null, false, noIdentifierReason(entityName));
    }

    // The pre-phase message, preserved verbatim so every entity that already resolved this way keeps
    // its exact outcome text (both public benchmarks stay byte-identical).
    public static String noIdentifierReason(String entityName) {
        return "entity '" + entityName + "' has no single @Id identifier, so no read-by-id "
                + "capability is modeled. Only single-id-parameter reads are in scope (Volume 5 "
                + "section 4.1); composite or non-@Id keys are a later shape, not an error here.";
    }

    // The pre-phase composite message, preserved verbatim.
    private static String directCompositeReason(ClassOrInterfaceDeclaration type, String entityName,
                                                List<VariableDeclarator> ownIds) {
        if (hasEmbeddedId(type)) {
            return "entity '" + entityName + "' has an @EmbeddedId composite key, so it forms no "
                    + "read-by-id capability and is never read by part of its key (Volume 5 "
                    + "section 4.2, condition 0).";
        }
        List<String> names = new ArrayList<>();
        for (VariableDeclarator id : ownIds) {
            names.add(id.getNameAsString());
        }
        return "entity '" + entityName + "' has a composite key: more than one @Id field " + names
                + ". It forms no read-by-id capability and is never read by part of its key (Volume 5 "
                + "section 4.2, condition 0).";
    }

    private static String inheritedCompositeReason(String entityName, List<String> ancestors,
                                                   boolean embeddedId) {
        return "entity '" + entityName + "' inherits a composite key from its superclass chain ("
                + (embeddedId ? "an @EmbeddedId" : "more than one @Id field") + " on " + ancestors
                + "), so it forms no read-by-id capability and is never read by part of its key "
                + "(Volume 5 section 4.13.2, condition 4).";
    }

    private static String ambiguousHierarchyReason(String entityName, List<String> ancestors,
                                                   List<VariableDeclarator> ids) {
        List<String> fields = new ArrayList<>();
        for (VariableDeclarator id : ids) {
            fields.add(id.getNameAsString());
        }
        return "entity '" + entityName + "' has an ambiguous inherited identifier: more than one "
                + "ancestor declares an @Id (" + ancestors + ", fields " + fields + "). The tool will "
                + "not pick one of two competing identifiers, so no read-by-id capability is modeled "
                + "(Volume 5 section 4.13.2, condition 4). This is distinct from a composite key.";
    }

    private static String parentNotInTreeReason(String entityName, String parent, String why) {
        return "entity '" + entityName + "' declares no @Id and extends '" + parent + "', but " + why
                + ", so the inherited identifier cannot be read. The tool never assumes an unseen "
                + "superclass carries an @Id (Volume 5 section 4.13.2, condition 2); no read-by-id "
                + "capability is modeled.";
    }

    private static Optional<ClassOrInterfaceType> superclassOf(ClassOrInterfaceDeclaration type) {
        return type.getExtendedTypes().isEmpty()
                ? Optional.empty()
                : Optional.of(type.getExtendedTypes().get(0));
    }

    private static List<VariableDeclarator> idFields(ClassOrInterfaceDeclaration type) {
        List<VariableDeclarator> ids = new ArrayList<>();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration) || !hasAnnotation(declaration, ID_ANNOTATION)) {
                continue;
            }
            ids.addAll(declaration.getVariables());
        }
        return ids;
    }

    private static boolean hasEmbeddedId(ClassOrInterfaceDeclaration type) {
        for (FieldDeclaration declaration : type.getFields()) {
            if (!isSkipped(declaration) && hasAnnotation(declaration, EMBEDDED_ID_ANNOTATION)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSkipped(FieldDeclaration declaration) {
        return declaration.hasModifier(Modifier.Keyword.STATIC)
                || hasAnnotation(declaration, TRANSIENT_ANNOTATION);
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> simpleName.equals(lastSegment(annotation.getNameAsString())));
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
