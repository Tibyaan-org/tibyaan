package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.ProviderException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only extractor enabled in the MVP (Volume 5 section 4, ADR-0011). It finds entity classes,
 * reads their persistent fields into the closed type set, and emits one {@link Entity} plus a
 * basic read only {@link Capability} (for example {@code getProduct}) per entity.
 *
 * <p>The capability's binding is a documented convention, {@code <Entity>Service#findById}. The
 * provider does not read services yet, so it names the conventional finder the runtime will
 * resolve. The binding is opaque in the IR by design (Volume 4 section 3.4).
 */
public final class EntityExtractor implements Extractor {

    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String ID_ANNOTATION = "Id";
    private static final String TRANSIENT_ANNOTATION = "Transient";
    private static final String COLUMN_ANNOTATION = "Column";
    private static final String NULLABLE_ANNOTATION = "Nullable";

    private final JavaTypeMapper typeMapper = new JavaTypeMapper();

    @Override
    public ExtractorContribution extract(List<CompilationUnit> units) {
        List<Entity> entities = new ArrayList<>();
        List<Capability> capabilities = new ArrayList<>();

        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() || !hasAnnotation(type, ENTITY_ANNOTATION)) {
                    continue;
                }
                entities.add(extractEntity(type));
                extractReadCapability(type).ifPresent(capabilities::add);
            }
        }
        return new ExtractorContribution(entities, capabilities, List.of());
    }

    private Entity extractEntity(ClassOrInterfaceDeclaration type) {
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

    // The MVP read capability getX(id) needs the entity's identifier, the field marked @Id.
    private java.util.Optional<Capability> extractReadCapability(ClassOrInterfaceDeclaration type) {
        String entityName = type.getNameAsString();
        for (FieldDeclaration declaration : type.getFields()) {
            if (isSkipped(declaration) || !hasAnnotation(declaration, ID_ANNOTATION)) {
                continue;
            }
            VariableDeclarator id = declaration.getVariable(0);
            Type idType = typeMapper.map(id.getTypeAsString()).orElseThrow(() -> new ProviderException(
                    "unmapped identifier type on entity '" + entityName + "'"));
            Map<String, Type> inputs = new LinkedHashMap<>();
            inputs.put(id.getNameAsString(), idType);
            Capability capability = Capability.read(
                    "get" + entityName,
                    inputs,
                    Type.entityRef(entityName),
                    new BindingRef(entityName + "Service#findById"));
            return java.util.Optional.of(capability);
        }
        // No identifier: emit the entity but no getX capability. Documented, not an error.
        return java.util.Optional.empty();
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
