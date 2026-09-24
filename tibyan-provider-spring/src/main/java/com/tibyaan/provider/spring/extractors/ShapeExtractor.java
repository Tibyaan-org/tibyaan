package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phase DL-2b, rules R2 and R3 (Volume 5 section 4.19.3): reads the wire form of an in-tree class the runtime
 * can construct from a JSON object, a DTO, and everything it reaches through its fields.
 *
 * <p>R2, which classes qualify: in tree; a class or a record; not an interface, not abstract, not an
 * {@code @Entity}, {@code @MappedSuperclass} or {@code @Embeddable}; constructible by structure (a record, a
 * class with no constructor or a no-argument one, or Lombok {@code @NoArgsConstructor} or {@code @Data} on
 * a class with no final instance field). R3, fields: every instance field of the class and its in-tree
 * superclasses, in declaration order, mapped as a primitive, an in-tree enum with its closed set, a
 * collection of either, or a nested shape; a self-referential or mutually referential shape refuses
 * ({@code SHAPE_CYCLE}); every other field is SKIPPED with its declared type disclosed, and a shape with no
 * mapped field refuses ({@code SHAPE_EMPTY}). The extractor never guesses a library type's shape.
 *
 * <p>The result is a set of shape DRAFTS keyed by qualified name with nested references by key; the intake
 * assigns display names over the whole set and builds the IR shapes (section 4.12.3's rule applied to
 * shapes).
 */
public final class ShapeExtractor {

    private static final Set<String> NEVER_INPUT_ANNOTATIONS = Set.of("Entity", "MappedSuperclass", "Embeddable");
    private static final Set<String> LOMBOK_SETTERS = Set.of("Data", "Setter");

    /** A field of a shape draft: typed by a primitive or, through {@code shapeKey}, by a nested shape. */
    public record FieldDraft(String name, String javaType, Primitive primitive, String shapeKey, boolean multiple,
                             List<String> values) {

        public boolean isNested() {
            return shapeKey != null;
        }
    }

    /** One shape as read from source, before display names are assigned. */
    public record ShapeDraft(String key, String simpleName, List<FieldDraft> fields, List<String> skipped,
                             TypeDeclaration<?> declaration) {
    }

    /** The outcome of extracting a parameter type: a shape (with everything it reaches), a refusal, or neither. */
    public record Outcome(String key, Map<String, ShapeDraft> shapes, String refusal) {

        public boolean isShape() {
            return key != null;
        }

        public boolean isRefused() {
            return refusal != null;
        }

        public static Outcome notAShape() {
            return new Outcome(null, Map.of(), null);
        }

        public static Outcome refused(String reason) {
            return new Outcome(null, Map.of(), reason);
        }
    }

    private final List<CompilationUnit> units;
    private final TypeReferenceResolver resolver;
    private final JavaTypeMapper typeMapper;
    private final EnumInputValues enumInputValues;

    public ShapeExtractor(List<CompilationUnit> units, TypeReferenceResolver resolver, JavaTypeMapper typeMapper,
                          EnumInputValues enumInputValues) {
        this.units = units;
        this.resolver = resolver;
        this.typeMapper = typeMapper;
        this.enumInputValues = enumInputValues;
    }

    /**
     * Extracts the shape of a parameter type written in source (already stripped of any collection), from the
     * compilation unit that references it. Not a shape when the type is not an in-tree class or record;
     * refused with a located reason when it is one the tool cannot construct or model.
     */
    public Outcome extract(String declaredType, CompilationUnit cu) {
        Map<String, ShapeDraft> shapes = new LinkedHashMap<>();
        Optional<String> refusal = extractInto(declaredType, cu, shapes, new ArrayList<>());
        if (refusal.isPresent()) {
            return Outcome.refused(refusal.get());
        }
        if (shapes.isEmpty()) {
            return Outcome.notAShape();
        }
        String key = resolver.resolve(declaredType, cu).fqcn();
        return new Outcome(key, shapes, null);
    }

    // Returns a refusal reason, or empty when the type is either not a shape at all (nothing added) or a
    // shape that was added with everything it reaches. `path` is the chain of keys being extracted, for
    // cycle detection.
    private Optional<String> extractInto(String declaredType, CompilationUnit cu, Map<String, ShapeDraft> shapes,
                                         List<String> path) {
        TypeReferenceResolver.Resolution resolution = resolver.resolve(declaredType, cu);
        if (resolution.isNotInTree() || resolution.kind() == TypeReferenceResolver.Kind.ENUM) {
            return Optional.empty();
        }
        if (resolution.isAmbiguous()) {
            return Optional.of("the type '" + declaredType + "' resolves to more than one in-tree declaration: "
                    + resolution.candidates() + " (section 4.12.4)");
        }
        String key = resolution.fqcn();
        if (resolution.kind() == TypeReferenceResolver.Kind.ENTITY) {
            return Optional.of("the type '" + declaredType + "' is the entity '" + key + "'; a persistence"
                    + " object is never an input the tool constructs (section 4.19.3, decision D8)");
        }
        if (shapes.containsKey(key)) {
            return Optional.empty();
        }
        if (path.contains(key)) {
            return Optional.of("SHAPE_CYCLE: '" + key + "' reaches itself through " + String.join(" -> ", path)
                    + "; a self-referential shape cannot be rendered as a finite schema (section 4.19.3, R3)");
        }
        Optional<TypeDeclaration<?>> found = declarationOf(key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TypeDeclaration<?> declaration = found.get();
        Optional<String> unqualified = qualifies(declaration, key);
        if (unqualified.isPresent()) {
            return unqualified;
        }

        path.add(key);
        List<FieldDraft> fields = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Optional<String> refusal = collectFields(declaration, cu, shapes, path, fields, skipped);
        path.remove(path.size() - 1);
        if (refusal.isPresent()) {
            return refusal;
        }
        if (fields.isEmpty()) {
            return Optional.of("SHAPE_EMPTY: '" + key + "' has no field the tool can model"
                    + (skipped.isEmpty() ? "" : " (skipped: " + String.join(", ", skipped) + ")")
                    + "; an empty object input is not honest (section 4.19.3, R3)");
        }
        shapes.put(key, new ShapeDraft(key, declaration.getNameAsString(), List.copyOf(fields),
                List.copyOf(skipped), declaration));
        return Optional.empty();
    }

    // R2: the structural conditions a class must meet before its fields are even read.
    private static Optional<String> qualifies(TypeDeclaration<?> declaration, String key) {
        for (String annotation : NEVER_INPUT_ANNOTATIONS) {
            if (hasAnnotation(declaration, annotation)) {
                return Optional.of("'" + key + "' is annotated @" + annotation + "; a persistence object is never"
                        + " an input the tool constructs (section 4.19.3, decision D8)");
            }
        }
        if (declaration instanceof RecordDeclaration) {
            return Optional.empty();
        }
        if (!(declaration instanceof ClassOrInterfaceDeclaration type)) {
            return Optional.of("'" + key + "' is not a class or a record (section 4.19.3, R2)");
        }
        if (type.isInterface()) {
            return Optional.of("'" + key + "' is an interface; the tool never guesses which implementation an"
                    + " agent means (section 4.19.3, R2)");
        }
        if (type.isAbstract()) {
            return Optional.of("'" + key + "' is abstract and cannot be constructed (section 4.19.3, R2)");
        }
        if (!constructible(type)) {
            return Optional.of("SHAPE_NOT_CONSTRUCTIBLE: '" + key + "' declares no no-argument constructor and"
                    + " carries neither @NoArgsConstructor nor a @Data with only non-final fields, so the"
                    + " runtime cannot build it from a JSON object (section 4.19.3, R2)");
        }
        return Optional.empty();
    }

    private static boolean constructible(ClassOrInterfaceDeclaration type) {
        List<ConstructorDeclaration> constructors = type.getConstructors();
        for (ConstructorDeclaration constructor : constructors) {
            if (constructor.getParameters().isEmpty()) {
                return true;
            }
        }
        if (hasAnnotation(type, "NoArgsConstructor")) {
            return true;
        }
        if (hasAnnotation(type, "Data") || hasAnnotation(type, "RequiredArgsConstructor")) {
            // Lombok's required-args constructor takes the final fields; with none it is a no-argument one.
            for (FieldDeclaration field : type.getFields()) {
                if (!field.hasModifier(Modifier.Keyword.STATIC) && field.hasModifier(Modifier.Keyword.FINAL)) {
                    return false;
                }
            }
            return true;
        }
        return constructors.isEmpty() && !hasAnnotation(type, "AllArgsConstructor")
                && !hasAnnotation(type, "Builder");
    }

    // R3: the fields, in-tree superclasses first (their fields are part of the object), then the class's
    // own, each mapped or skipped by name.
    private Optional<String> collectFields(TypeDeclaration<?> declaration, CompilationUnit referencingCu,
                                           Map<String, ShapeDraft> shapes, List<String> path,
                                           List<FieldDraft> fields, List<String> skipped) {
        CompilationUnit declaringCu = declaration.findCompilationUnit().orElse(referencingCu);
        if (declaration instanceof ClassOrInterfaceDeclaration type) {
            for (ClassOrInterfaceType extended : type.getExtendedTypes()) {
                String superName = extended.getNameWithScope();
                TypeReferenceResolver.Resolution superResolution = resolver.resolve(superName, declaringCu);
                Optional<TypeDeclaration<?>> superDeclaration = superResolution.isResolved()
                        ? declarationOf(superResolution.fqcn())
                        : Optional.empty();
                if (superDeclaration.isEmpty()) {
                    skipped.add("extends " + superName + ": superclass not in tree, its fields are unknown");
                    continue;
                }
                Optional<String> refusal = collectFields(superDeclaration.get(), declaringCu, shapes, path,
                        fields, skipped);
                if (refusal.isPresent()) {
                    return refusal;
                }
            }
            boolean classSettable = hasAnnotation(type, "Data") || hasAnnotation(type, "Setter");
            for (FieldDeclaration field : type.getFields()) {
                if (field.hasModifier(Modifier.Keyword.STATIC) || field.hasModifier(Modifier.Keyword.TRANSIENT)
                        || hasAnnotation(field, "Transient") || hasAnnotation(field, "JsonIgnore")) {
                    continue;
                }
                for (VariableDeclarator variable : field.getVariables()) {
                    String name = variable.getNameAsString();
                    String javaType = variable.getType().asString();
                    if (!classSettable && !field.isPublic() && !hasAnnotation(field, "Setter")
                            && type.getMethodsByName("set" + capitalize(name)).stream()
                                    .noneMatch(m -> m.getParameters().size() == 1)) {
                        skipped.add(name + ": " + javaType + " (no setter, the runtime could not set it)");
                        continue;
                    }
                    Optional<String> refusal = mapField(name, javaType, declaringCu, shapes, path, fields, skipped);
                    if (refusal.isPresent()) {
                        return refusal;
                    }
                }
            }
        } else if (declaration instanceof RecordDeclaration record) {
            for (Parameter component : record.getParameters()) {
                Optional<String> refusal = mapField(component.getNameAsString(), component.getType().asString(),
                        declaringCu, shapes, path, fields, skipped);
                if (refusal.isPresent()) {
                    return refusal;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> mapField(String name, String javaType, CompilationUnit cu, Map<String, ShapeDraft> shapes,
                                      List<String> path, List<FieldDraft> fields, List<String> skipped) {
        CollectionParameter.Outcome collection = CollectionParameter.classify(javaType);
        if (collection.isRefused()) {
            skipped.add(name + ": " + javaType + " (" + collection.refusal() + ")");
            return Optional.empty();
        }
        boolean multiple = collection.isCollection();
        String elementType = multiple ? collection.element() : javaType;

        EnumInputValues.Outcome enumOutcome = enumInputValues.resolve(elementType, cu);
        if (enumOutcome.isRefused()) {
            skipped.add(name + ": " + javaType + " (" + enumOutcome.refusal() + ")");
            return Optional.empty();
        }
        if (enumOutcome.isProvable()) {
            fields.add(new FieldDraft(name, javaType, Primitive.STRING, null, multiple, enumOutcome.values()));
            return Optional.empty();
        }
        Optional<Type> primitive = typeMapper.map(elementType);
        if (primitive.isPresent()) {
            fields.add(new FieldDraft(name, javaType, (Primitive) primitive.get(), null, multiple, List.of()));
            return Optional.empty();
        }
        TypeReferenceResolver.Resolution resolution = resolver.resolve(elementType, cu);
        if (resolution.isResolved() && resolution.kind() == TypeReferenceResolver.Kind.ENTITY) {
            // Decision D8: a persistence object inside a DTO is disclosed, never constructed.
            skipped.add(name + ": " + javaType + " (entity, never an input: decision D8)");
            return Optional.empty();
        }
        if (resolution.isResolved() && resolution.kind() == TypeReferenceResolver.Kind.CLASS) {
            int before = shapes.size();
            boolean known = shapes.containsKey(resolution.fqcn());
            Optional<String> refusal = extractInto(elementType, cu, shapes, path);
            if (refusal.isPresent()) {
                if (refusal.get().startsWith("SHAPE_CYCLE")) {
                    return refusal;
                }
                skipped.add(name + ": " + javaType + " (" + refusal.get() + ")");
                return Optional.empty();
            }
            if (known || shapes.size() > before) {
                fields.add(new FieldDraft(name, javaType, null, resolution.fqcn(), multiple, List.of()));
                return Optional.empty();
            }
        }
        skipped.add(name + ": " + javaType + skipReason(elementType));
        return Optional.empty();
    }

    /** The in-tree class or record declared under the qualified name, if any. */
    public Optional<TypeDeclaration<?>> declarationOf(String fqcn) {
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.getFullyQualifiedName().map(fqcn::equals).orElse(false)) {
                    return Optional.of(type);
                }
            }
            for (RecordDeclaration record : unit.findAll(RecordDeclaration.class)) {
                if (record.getFullyQualifiedName().map(fqcn::equals).orElse(false)) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    // Matched by the last segment, so a qualified spelling (@lombok.Data, @jakarta.persistence.Entity) counts.
    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream().anyMatch(a -> {
            String name = a.getNameAsString();
            int dot = name.lastIndexOf('.');
            return (dot >= 0 ? name.substring(dot + 1) : name).equals(simpleName);
        });
    }

    // Why a field the tool does not model was skipped: an in-tree interface (never guessed), or a type that
    // is not in the scanned tree at all.
    private String skipReason(String elementType) {
        String simple = elementType.contains(".") ? elementType.substring(elementType.lastIndexOf('.') + 1) : elementType;
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() && type.getNameAsString().equals(simple)) {
                    return " (interface: the tool never guesses which implementation an agent means)";
                }
            }
        }
        return " (not in the scanned tree)";
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
