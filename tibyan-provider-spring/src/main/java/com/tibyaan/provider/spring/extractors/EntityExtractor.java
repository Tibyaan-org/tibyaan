package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.ProviderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads entity classes (Volume 5 section 4.1). It finds {@code @Entity} types, reads their
 * persistent fields into the closed type set, and, for each entity that has an identifier, derives
 * a {@link ReadIntent} for a {@code get<Entity>} read.
 *
 * <p>It deliberately does not produce the capability's binding. The binding is the real service
 * method, discovered by joining the read intent against the service-method catalog in the
 * provider (Volume 5 section 4.1). Keeping the entity side binding-free is what lets this
 * extractor stay independent of {@link ServiceExtractor}.
 *
 * <p>Extraction is exposed per type ({@link #entityTypes}, {@link #extractEntity},
 * {@link #readIntent}) so the provider can drive the loop and collect-and-continue past a single
 * bad entity (Volume 5 section 4, phase E1). Each of these still fails loudly for the one entity it
 * cannot map; the choice to record that and continue, or to abort, belongs to the provider.
 */
public final class EntityExtractor {

    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String ID_ANNOTATION = "Id";
    private static final String TRANSIENT_ANNOTATION = "Transient";
    private static final String COLUMN_ANNOTATION = "Column";
    private static final String NULLABLE_ANNOTATION = "Nullable";

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
     * Reads one entity into IR, or fails loudly if a field's type has no equivalent in the closed
     * primitive set. The provider decides whether that failure aborts the scan or is recorded.
     */
    public Entity extractEntity(ClassOrInterfaceDeclaration type) {
        String entityName = type.getNameAsString();
        List<Field> fields = new ArrayList<>();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration)) {
                continue;
            }
            boolean nullable = isNullable(declaration);
            for (VariableDeclarator variable : declaration.getVariables()) {
                fields.add(toField(entityName, variable, nullable));
            }
        }
        return new Entity(entityName, fields, List.of(), Metadata.empty());
    }

    private Field toField(String entityName, VariableDeclarator variable, boolean nullable) {
        String fieldName = variable.getNameAsString();
        String javaType = variable.getTypeAsString();
        Type type = typeMapper.map(javaType).orElseThrow(() -> new ProviderException(
                "unmapped Java type '" + javaType + "' on field '" + entityName + "." + fieldName
                        + "'. The closed primitive type set (Volume 4 section 4) has no equivalent. "
                        + "Extend the type mapping or exclude the field; do not coerce it."));
        return new Field(fieldName, type, nullable, List.of());
    }

    // The MVP read getX(id) needs the entity's identifier, the field marked @Id. We emit the
    // intent (what to read and the id's type), not the binding: the backing method is discovered
    // later by the provider's join against the service catalog (Volume 5 section 4.1). Empty means
    // the entity has no single @Id, a documented out-of-scope shape, not an error here.
    public Optional<ReadIntent> readIntent(ClassOrInterfaceDeclaration type) {
        String entityName = type.getNameAsString();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration) || !hasAnnotation(declaration, ID_ANNOTATION)) {
                continue;
            }
            VariableDeclarator id = declaration.getVariable(0);
            Type idType = typeMapper.map(id.getTypeAsString()).orElseThrow(() -> new ProviderException(
                    "unmapped identifier type on entity '" + entityName + "'"));
            return Optional.of(new ReadIntent(
                    entityName, "get" + entityName, id.getNameAsString(), idType));
        }
        // No identifier: emit the entity but no read intent. Documented, not an error.
        return Optional.empty();
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
            if (COLUMN_ANNOTATION.equals(simpleName(annotation.getNameAsString()))
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
                .anyMatch(annotation -> simpleName.equals(simpleName(annotation.getNameAsString())));
    }

    private static String simpleName(String annotationName) {
        int dot = annotationName.lastIndexOf('.');
        return dot >= 0 ? annotationName.substring(dot + 1) : annotationName;
    }
}
