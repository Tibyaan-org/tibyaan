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
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.extractors.TypeRegistry.Kind;

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
     * Reads one entity into IR together with the relations its associations contribute, or fails
     * loudly if a field's type has no equivalent. The registry resolves association and enum types
     * structurally (sections 4.4, 4.5).
     */
    public ExtractedEntity extractEntity(ClassOrInterfaceDeclaration type, TypeRegistry registry) {
        String entityName = type.getNameAsString();
        List<Field> fields = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration)) {
                continue;
            }
            boolean nullable = isNullable(declaration);
            for (VariableDeclarator variable : declaration.getVariables()) {
                fields.add(toField(entityName, declaration, variable, nullable, registry, relations));
            }
        }
        return new ExtractedEntity(new Entity(entityName, fields, List.of(), Metadata.empty()), relations);
    }

    // One field to IR (sections 4.4, 4.5, 4.6). Order: primitive first (the closed set, FLOAT
    // included); then the collection refusal (a to-many shape is deferred); then structural
    // resolution of the declared type in-tree, which decides association, enum, or a located
    // refusal. Nothing is decided by a type's name resemblance.
    private Field toField(String entityName, FieldDeclaration declaration, VariableDeclarator variable,
                          boolean nullable, TypeRegistry registry, List<Relation> relations) {
        String fieldName = variable.getNameAsString();
        String javaType = variable.getTypeAsString();

        Optional<Type> primitive = typeMapper.map(javaType);
        if (primitive.isPresent()) {
            return new Field(fieldName, primitive.get(), nullable, List.of());
        }

        if (isCollectionOrArray(javaType)) {
            throw new ProviderException(
                    "collection field '" + entityName + "." + fieldName + "' of type '" + javaType
                            + "' is a to-many shape, deferred to the list-modeling phase (Volume 5 "
                            + "section 4.4); it is not mapped as a single reference.");
        }

        String simpleName = lastSegment(javaType);
        Optional<Kind> kind = registry.kindOf(simpleName);
        if (kind.isEmpty()) {
            throw new ProviderException(
                    "unresolved type '" + javaType + "' on field '" + entityName + "." + fieldName
                            + "'. It is neither a closed primitive nor a type declared in the scanned "
                            + "source tree; resolution is structural (Volume 5 sections 4.4, 4.5), so "
                            + "a type that only looks like an entity or enum name does not map.");
        }
        switch (kind.get()) {
            case ENTITY:
                return toAssociationField(entityName, declaration, fieldName, simpleName, nullable, relations);
            case ENUM:
                return toEnumField(fieldName, simpleName, nullable, registry);
            case CLASS:
            default:
                throw new ProviderException(
                        "unmapped type '" + javaType + "' on field '" + entityName + "." + fieldName
                                + "'. It resolves in-tree to a class that is neither an @Entity nor an "
                                + "enum (Volume 5 sections 4.4, 4.5); it has no place in the closed type "
                                + "set and is not coerced.");
        }
    }

    // Section 4.4: an entity-typed field maps to ENTITY_REF plus a Relation only when the source
    // itself declares the relationship with @ManyToOne or @OneToOne. The kind is read from that
    // annotation, never inferred. An @Id association still maps here; read eligibility is separate.
    private Field toAssociationField(String entityName, FieldDeclaration declaration, String fieldName,
                                     String target, boolean nullable, List<Relation> relations) {
        Optional<RelationKind> relationKind = declaredRelationKind(declaration);
        if (relationKind.isEmpty()) {
            throw new ProviderException(
                    "entity-typed field '" + entityName + "." + fieldName + "' references entity '"
                            + target + "' but declares no @ManyToOne or @OneToOne; the relationship "
                            + "is not stated in the source and the provider will not infer one "
                            + "(Volume 5 section 4.4).");
        }
        relations.add(new Relation(entityName, target, relationKind.get()));
        return new Field(fieldName, Type.entityRef(target), nullable, List.of());
    }

    // Section 4.5: an enum-typed field maps to STRING and carries the closed value set as an
    // annotation, read from the enum declaration in declaration order. @Enumerated is irrelevant:
    // the wire form is the constant name in every storage mode.
    private static Field toEnumField(String fieldName, String enumType, boolean nullable,
                                     TypeRegistry registry) {
        List<String> constants = registry.enumConstants(enumType);
        Annotation values = new Annotation(ENUM_ANNOTATION_KEY, constants);
        return new Field(fieldName, Primitive.STRING, nullable, List.of(values));
    }

    // The MVP read getX(id) needs the entity's identifier, the field marked @Id, mapped to a
    // bindable primitive (section 4.1). A FLOAT @Id forms no read intent: an inexact primary key is
    // not a lookup key the tool will bind (section 4.6, rule 4). Empty means no usable identifier;
    // the provider turns that into a located outcome.
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
                entityName, "get" + entityName, id.getNameAsString(), idType.get()));
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

    private static boolean isCollectionOrArray(String javaType) {
        return javaType.contains("<") || javaType.contains("[")
                || COLLECTION_TYPES.contains(lastSegment(javaType));
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
