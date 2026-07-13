package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.RelationKind;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads entity classes (Volume 5 sections 4.1, 4.4, 4.5, 4.6). It finds {@code @Entity} types,
 * reads their persistent fields into the closed type set, and, for each entity that has a usable
 * identifier, derives a {@link ReadIntent} for a {@code get<Entity>} read.
 *
 * <p>Field mapping covers primitives (including FLOAT, section 4.6), associations to other entities
 * (section 4.4: an {@code ENTITY_REF} field plus a {@link Relation}), and enums (section 4.5: a
 * STRING field carrying an {@code enum} annotation with the constant names). Association and enum
 * resolution is structural, through the {@link TypeRegistry}, never by name. A field whose type has
 * no equivalent fails loudly with a located, enriched message; the provider decides whether that
 * aborts the scan or is recorded (phase E1).
 *
 * <p>It deliberately does not produce the capability's binding. The binding is the real service
 * method, discovered by the provider's join against the service catalog (section 4.1).
 */
public final class EntityExtractor {

    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String ID_ANNOTATION = "Id";
    private static final String EMBEDDED_ID_ANNOTATION = "EmbeddedId";
    private static final String TRANSIENT_ANNOTATION = "Transient";
    private static final String COLUMN_ANNOTATION = "Column";
    private static final String NULLABLE_ANNOTATION = "Nullable";
    private static final String MANY_TO_ONE_ANNOTATION = "ManyToOne";
    private static final String ONE_TO_ONE_ANNOTATION = "OneToOne";
    private static final String ENUM_ANNOTATION_KEY = "enum";

    // Collection container types whose raw simple name signals a to-many shape, deferred to a later
    // phase (section 4.4). Generic and array syntax is caught separately by the type string.
    private static final Set<String> COLLECTION_TYPES =
            Set.of("List", "Set", "Map", "Collection", "Iterable");

    private final JavaTypeMapper typeMapper = new JavaTypeMapper();

    /** Every {@code @Entity} class declared across the parsed units, in walk order. */
    public List<ClassOrInterfaceDeclaration> entityTypes(List<CompilationUnit> units) {
        List<ClassOrInterfaceDeclaration> types = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!type.isInterface() && hasAnnotation(type, ENTITY_ANNOTATION)) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    /**
     * Reads one entity into IR together with the relations its associations contribute and the
     * fields tolerance skipped. Extraction no longer fails on an unmappable field (partial-model
     * integrity, Volume 5 section 4.8): a field the closed set and the association and enum rules
     * cannot map is skipped with a located note and the entity extracts without it. The registry
     * resolves association and enum types structurally (sections 4.4, 4.5).
     */
    public ExtractedEntity extractEntity(ClassOrInterfaceDeclaration type, TypeRegistry registry,
                                         TypeReferenceResolver resolver) {
        String entityName = type.getNameAsString();
        // Phase Entity Identity (Volume 5 section 4.12.2): the entity's stable identity is its
        // fully-qualified class name (the canonical FQCN including any enclosing type). The display
        // name starts as the simple name; the provider's batch fixpoint (section 4.12.3) refines it
        // to a disambiguated name only where a simple name collides across the model. Every reference
        // this extractor emits, an ENTITY_REF field, a relation end, or a skipped field's owner, is
        // keyed by FQCN here; the provider remaps those keys to display names once the fixpoint runs.
        String key = entityKey(type);
        CompilationUnit unit = type.findCompilationUnit().orElse(null);
        List<Field> fields = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        List<SkippedField> skipped = new ArrayList<>();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration)) {
                continue;
            }
            boolean nullable = isNullable(declaration);
            for (VariableDeclarator variable : declaration.getVariables()) {
                toField(key, entityName, unit, declaration, variable, nullable, registry, resolver,
                        fields, relations, skipped);
            }
        }
        return new ExtractedEntity(
                new Entity(key, entityName, fields, List.of(), Metadata.empty()), relations, skipped);
    }

    // The entity's key (Volume 5 section 4.12.2): the fully-qualified class name, falling back to the
    // simple name only when the FQN is unresolvable (a source without a package declaration).
    private static String entityKey(ClassOrInterfaceDeclaration type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }

    // One field to IR (sections 4.4, 4.5, 4.6), or a skip with a located note (section 4.8). Order:
    // primitive first (the closed set, FLOAT included); then the to-many and binary/array skip; then
    // structural resolution of the declared type in-tree, which decides association, enum, or a
    // skip. Nothing is decided by a type's name resemblance, and nothing vetoes the entity: a field
    // the rules cannot map is recorded as a skipped field and the entity extracts without it.
    private void toField(String entityKey, String entitySimple, CompilationUnit unit,
                         FieldDeclaration declaration, VariableDeclarator variable, boolean nullable,
                         TypeRegistry registry, TypeReferenceResolver resolver, List<Field> fields,
                         List<Relation> relations, List<SkippedField> skipped) {
        String fieldName = variable.getNameAsString();
        String javaType = variable.getTypeAsString();

        Optional<Type> primitive = typeMapper.map(javaType);
        if (primitive.isPresent()) {
            fields.add(new Field(fieldName, primitive.get(), nullable, List.of()));
            return;
        }

        // Section 4.8, rule 2: byte[] and other scalar arrays are binary blobs, not to-many shapes.
        if (isArray(javaType)) {
            skipped.add(new SkippedField(entityKey, fieldName, "binary or array field of type '"
                    + javaType + "' has no scalar mapping in the closed set (Volume 5 section 4.8); "
                    + "skipped, not modeled as a value."));
            return;
        }
        // Section 4.8, rule 1: a to-many collection is deferred to the list phase, skipped here.
        if (isCollection(javaType)) {
            skipped.add(new SkippedField(entityKey, fieldName, "to-many field of type '" + javaType
                    + "' deferred to the list-modeling phase (E4, Volume 5 section 4.8); skipped, "
                    + "not modeled."));
            return;
        }

        // Section 4.12.4: resolve the declared type to a single in-tree declaration by key, against
        // this entity's compilation unit. A unique simple name resolves by the fast path (byte-identical
        // to the pre-phase last-segment lookup); a colliding simple name resolves to one type via Java
        // scoping or refuses, never guessing which same-named type was meant.
        if (unit == null) {
            skipped.add(new SkippedField(entityKey, fieldName, "type '" + javaType + "' is not "
                    + "resolvable in the scanned source tree (Volume 5 section 4.8); skipped."));
            return;
        }
        TypeReferenceResolver.Resolution resolution = resolver.resolve(javaType, unit);
        if (resolution.isNotInTree()) {
            // Section 4.8, rule 4: a type not in the scanned tree (a library type) is skipped.
            skipped.add(new SkippedField(entityKey, fieldName, "type '" + javaType + "' is not "
                    + "resolvable in the scanned source tree (Volume 5 section 4.8); skipped."));
            return;
        }
        if (resolution.isAmbiguous()) {
            // Section 4.12.4, step d: an ambiguous cross-context reference is refused loud, its
            // candidates named, never guessed. No reference is formed and the entity extracts without
            // the field (partial-model integrity, section 4.8).
            skipped.add(new SkippedField(entityKey, fieldName, "entity-typed field of type '" + javaType
                    + "' is an ambiguous cross-context reference: it resolves to more than one in-tree "
                    + "type " + resolution.candidates() + ", and the resolver will not guess which was "
                    + "meant (Volume 5 section 4.12.4); skipped, no reference formed."));
            return;
        }
        String targetKey = resolution.fqcn();
        String targetSimple = lastSegment(javaType);
        switch (resolution.kind()) {
            case ENTITY:
                toAssociationField(entityKey, declaration, fieldName, targetKey, targetSimple, nullable,
                        fields, relations, skipped);
                return;
            case ENUM:
                fields.add(toEnumField(fieldName, targetKey, nullable, resolver));
                return;
            case CLASS:
            default:
                // Section 4.8, rule 3: an in-tree value type (for example @Embeddable) is skipped.
                String embeddable = registry.isEmbeddable(targetSimple) ? " (an @Embeddable)" : "";
                skipped.add(new SkippedField(entityKey, fieldName, "in-tree value type '" + javaType
                        + "'" + embeddable + ", neither an @Entity nor an enum (Volume 5 section 4.8); "
                        + "skipped."));
        }
    }

    // Section 4.4: an entity-typed field maps to ENTITY_REF plus a Relation only when the source
    // itself declares the relationship with @ManyToOne or @OneToOne. The kind is read from that
    // annotation, never inferred. Section 4.8, rule 5: an entity-typed field with no declared
    // relationship is skipped rather than inferred, and the entity extracts without it.
    private void toAssociationField(String entityKey, FieldDeclaration declaration, String fieldName,
                                    String targetKey, String targetSimple, boolean nullable,
                                    List<Field> fields, List<Relation> relations,
                                    List<SkippedField> skipped) {
        Optional<RelationKind> relationKind = declaredRelationKind(declaration);
        if (relationKind.isEmpty()) {
            skipped.add(new SkippedField(entityKey, fieldName, "entity-typed field referencing '"
                    + targetSimple + "' declares no @ManyToOne or @OneToOne (Volume 5 sections 4.4, "
                    + "4.8); the relationship is not stated in the source, so it is skipped, not "
                    + "inferred."));
            return;
        }
        // The relation ends and the ENTITY_REF carry keys (section 4.12.4); the provider remaps them
        // to display names once the disambiguation fixpoint has run.
        relations.add(new Relation(entityKey, targetKey, relationKind.get()));
        fields.add(new Field(fieldName, Type.entityRef(targetKey), nullable, List.of()));
    }

    // Section 4.5: an enum-typed field maps to STRING and carries the closed value set as an
    // annotation, read from the enum declaration in declaration order. @Enumerated is irrelevant:
    // the wire form is the constant name in every storage mode.
    private static Field toEnumField(String fieldName, String enumFqcn, boolean nullable,
                                     TypeReferenceResolver resolver) {
        // Section 4.12.4: the enum's constants are read from the resolved declaration by FQCN, so two
        // same-named enums in different packages no longer collapse to whichever was parsed last.
        List<String> constants = resolver.enumConstants(enumFqcn);
        Annotation values = new Annotation(ENUM_ANNOTATION_KEY, constants);
        return new Field(fieldName, Primitive.STRING, nullable, List.of(values));
    }

    // Eligibility (section 4.2, condition 0): an entity with an @EmbeddedId, or with more than one
    // @Id field, has a composite key. It forms no read intent and is never read by part of its key.
    // This is separate from field mapping: an @Id association field still maps (section 4.4).
    public boolean hasCompositeKey(ClassOrInterfaceDeclaration type) {
        return idFieldCount(type) > 1 || hasEmbeddedId(type);
    }

    // A located reason for the composite-key refusal, naming what was seen (section 4.2.4).
    public String compositeKeyReason(ClassOrInterfaceDeclaration type) {
        String entityName = type.getNameAsString();
        if (hasEmbeddedId(type)) {
            return "entity '" + entityName + "' has an @EmbeddedId composite key, so it forms no "
                    + "read-by-id capability and is never read by part of its key (Volume 5 "
                    + "section 4.2, condition 0).";
        }
        List<String> idFields = new ArrayList<>();
        for (FieldDeclaration declaration : type.getFields()) {
            if (!isSkipped(declaration) && hasAnnotation(declaration, ID_ANNOTATION)) {
                for (VariableDeclarator variable : declaration.getVariables()) {
                    idFields.add(variable.getNameAsString());
                }
            }
        }
        return "entity '" + entityName + "' has a composite key: more than one @Id field " + idFields
                + ". It forms no read-by-id capability and is never read by part of its key (Volume 5 "
                + "section 4.2, condition 0).";
    }

    private static int idFieldCount(ClassOrInterfaceDeclaration type) {
        int count = 0;
        for (FieldDeclaration declaration : type.getFields()) {
            if (!isSkipped(declaration) && hasAnnotation(declaration, ID_ANNOTATION)) {
                count += declaration.getVariables().size();
            }
        }
        return count;
    }

    private static boolean hasEmbeddedId(ClassOrInterfaceDeclaration type) {
        for (FieldDeclaration declaration : type.getFields()) {
            if (!isSkipped(declaration) && hasAnnotation(declaration, EMBEDDED_ID_ANNOTATION)) {
                return true;
            }
        }
        return false;
    }

    // The MVP read getX(id) needs the entity's identifier, the field marked @Id, mapped to a
    // bindable primitive (section 4.1). A FLOAT @Id forms no read intent: an inexact primary key is
    // not a lookup key the tool will bind (section 4.6, rule 4). Empty means no usable identifier;
    // the provider turns that into a located outcome. Callers check hasCompositeKey first.
    public Optional<ReadIntent> readIntent(ClassOrInterfaceDeclaration type) {
        Optional<VariableDeclarator> idField = firstIdField(type);
        if (idField.isEmpty()) {
            return Optional.empty();
        }
        VariableDeclarator id = idField.get();
        String entityName = type.getNameAsString();
        Optional<Type> idType = typeMapper.map(id.getTypeAsString());
        if (idType.isEmpty() || idType.get() == Primitive.FLOAT) {
            return Optional.empty();
        }
        return Optional.of(new ReadIntent(
                entityName, entityKey(type), "get" + entityName, id.getNameAsString(), idType.get()));
    }

    // The located reason an entity formed no read intent, for the provider's outcome. It
    // distinguishes an inexact-id refusal (section 4.6) from a missing or non-primitive identifier,
    // without adding a new cause code.
    public String noReadIntentReason(ClassOrInterfaceDeclaration type) {
        String entityName = type.getNameAsString();
        Optional<VariableDeclarator> idField = firstIdField(type);
        if (idField.isEmpty()) {
            return "entity '" + entityName + "' has no single @Id identifier, so no read-by-id "
                    + "capability is modeled. Only single-id-parameter reads are in scope (Volume 5 "
                    + "section 4.1); composite or non-@Id keys are a later shape, not an error here.";
        }
        VariableDeclarator id = idField.get();
        Optional<Type> idType = typeMapper.map(id.getTypeAsString());
        if (idType.isPresent() && idType.get() == Primitive.FLOAT) {
            return "entity '" + entityName + "' has @Id '" + id.getNameAsString() + "' of type '"
                    + id.getTypeAsString() + "', which maps to the inexact FLOAT primitive. An "
                    + "inexact identifier forms no read intent (Volume 5 section 4.6); the tool will "
                    + "not bind a lookup to a floating-point key.";
        }
        return "entity '" + entityName + "' has @Id '" + id.getNameAsString() + "' of type '"
                + id.getTypeAsString() + "', which does not map to a bindable primitive, so no "
                + "read-by-id capability is modeled (Volume 5 section 4.1).";
    }

    private static Optional<VariableDeclarator> firstIdField(ClassOrInterfaceDeclaration type) {
        for (FieldDeclaration declaration : type.getFields()) {
            if (!isSkipped(declaration) && hasAnnotation(declaration, ID_ANNOTATION)) {
                return Optional.of(declaration.getVariable(0));
            }
        }
        return Optional.empty();
    }

    private static Optional<RelationKind> declaredRelationKind(FieldDeclaration declaration) {
        if (hasAnnotation(declaration, MANY_TO_ONE_ANNOTATION)) {
            return Optional.of(RelationKind.MANY_TO_ONE);
        }
        if (hasAnnotation(declaration, ONE_TO_ONE_ANNOTATION)) {
            return Optional.of(RelationKind.ONE_TO_ONE);
        }
        return Optional.empty();
    }

    private static boolean isArray(String javaType) {
        return javaType.contains("[");
    }

    private static boolean isCollection(String javaType) {
        return javaType.contains("<") || COLLECTION_TYPES.contains(lastSegment(javaType));
    }

    private static boolean isSkipped(FieldDeclaration declaration) {
        return declaration.hasModifier(Modifier.Keyword.STATIC)
                || hasAnnotation(declaration, TRANSIENT_ANNOTATION);
    }

    // Fields are non nullable by default; a field is nullable only when it says so explicitly,
    // via @Nullable or @Column(nullable = true). This matches the Volume 4 example, where every
    // field is non nullable, and is the conservative reading of a plain JPA field.
    private static boolean isNullable(FieldDeclaration declaration) {
        if (hasAnnotation(declaration, NULLABLE_ANNOTATION)) {
            return true;
        }
        for (AnnotationExpr annotation : declaration.getAnnotations()) {
            if (COLUMN_ANNOTATION.equals(lastSegment(annotation.getNameAsString()))
                    && annotation.isNormalAnnotationExpr()) {
                for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                    if ("nullable".equals(pair.getNameAsString())) {
                        return "true".equals(pair.getValue().toString());
                    }
                }
            }
        }
        return false;
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
