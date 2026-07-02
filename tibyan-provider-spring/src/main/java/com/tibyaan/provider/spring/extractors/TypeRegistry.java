package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the scanned source declares, indexed by simple type name (Volume 5 sections 4.4 and 4.5).
 *
 * <p>The association and enum rules resolve a field's declared type "in the same scanned source
 * tree": a type is mapped only when its declaration is found here, never by name resemblance. This
 * registry is that lookup, built once from all parsed units and consulted per field. Resolution is
 * by simple name, matching the rule text (the caller reduces a fully qualified type to its last
 * segment first).
 *
 * <p>A simple name that resolves to declarations of more than one kind is ambiguous and does not
 * resolve: {@link #kindOf} returns empty, so the field refuses rather than guessing which
 * declaration was meant. This keeps resolution structural and deterministic.
 */
public final class TypeRegistry {

    /** What a resolved simple name is, in the source. */
    public enum Kind {
        /** A class annotated {@code @Entity}. */
        ENTITY,
        /** An {@code enum} declaration. */
        ENUM,
        /** Any other class or interface: a value object, a library type, a key class. */
        CLASS
    }

    private static final String ENTITY_ANNOTATION = "Entity";
    private static final String EMBEDDABLE_ANNOTATION = "Embeddable";

    private final Map<String, Kind> kindBySimpleName;
    private final Map<String, List<String>> enumConstantsBySimpleName;
    private final Set<String> embeddableSimpleNames;

    private TypeRegistry(Map<String, Kind> kindBySimpleName,
                         Map<String, List<String>> enumConstantsBySimpleName,
                         Set<String> embeddableSimpleNames) {
        this.kindBySimpleName = kindBySimpleName;
        this.enumConstantsBySimpleName = enumConstantsBySimpleName;
        this.embeddableSimpleNames = embeddableSimpleNames;
    }

    /** Builds the registry from every type declared across the parsed units. */
    public static TypeRegistry from(List<CompilationUnit> units) {
        Map<String, Kind> kinds = new HashMap<>();
        Map<String, Kind> ambiguous = new HashMap<>();
        Map<String, List<String>> enumConstants = new LinkedHashMap<>();
        Set<String> embeddables = new HashSet<>();

        for (CompilationUnit unit : units) {
            for (EnumDeclaration decl : unit.findAll(EnumDeclaration.class)) {
                String name = decl.getNameAsString();
                record(kinds, ambiguous, name, Kind.ENUM);
                enumConstants.put(name, constantNames(decl));
            }
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface()) {
                    continue;
                }
                Kind kind = hasAnnotation(decl, ENTITY_ANNOTATION) ? Kind.ENTITY : Kind.CLASS;
                record(kinds, ambiguous, decl.getNameAsString(), kind);
                if (hasAnnotation(decl, EMBEDDABLE_ANNOTATION)) {
                    embeddables.add(decl.getNameAsString());
                }
            }
        }

        // A name seen with more than one kind cannot resolve: drop it so kindOf returns empty.
        for (String name : ambiguous.keySet()) {
            kinds.remove(name);
            enumConstants.remove(name);
        }
        return new TypeRegistry(kinds, enumConstants, embeddables);
    }

    /** The kind of the type with this simple name, or empty when it is not declared or is ambiguous. */
    public Optional<Kind> kindOf(String simpleName) {
        return Optional.ofNullable(kindBySimpleName.get(simpleName));
    }

    /** Whether the type with this simple name is a class annotated {@code @Embeddable}, for a clearer skip note. */
    public boolean isEmbeddable(String simpleName) {
        return embeddableSimpleNames.contains(simpleName);
    }

    /**
     * The constant names of the enum with this simple name, in declaration order. Empty when the
     * name is not an unambiguous enum.
     */
    public List<String> enumConstants(String simpleName) {
        if (kindBySimpleName.get(simpleName) != Kind.ENUM) {
            return List.of();
        }
        return enumConstantsBySimpleName.getOrDefault(simpleName, List.of());
    }

    private static void record(Map<String, Kind> kinds, Map<String, Kind> ambiguous,
                               String name, Kind kind) {
        Kind existing = kinds.putIfAbsent(name, kind);
        if (existing != null && existing != kind) {
            ambiguous.put(name, kind);
        }
    }

    private static List<String> constantNames(EnumDeclaration decl) {
        List<String> names = new ArrayList<>();
        for (EnumConstantDeclaration constant : decl.getEntries()) {
            names.add(constant.getNameAsString());
        }
        return names;
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
