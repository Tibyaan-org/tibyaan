package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a Java type reference to a single in-tree fully-qualified name, over EVERY declared type,
 * classes, interfaces, and enums alike (Volume 5 section 4.14, condition 5 and the constant rule).
 *
 * <p>This is the section 4.12.4 scoping stage applied to the whole type set. {@link
 * TypeReferenceResolver} deliberately indexes only classes and enums, because it exists to tie a
 * reference to an {@code @Entity} key; controller discovery needs to resolve a service field typed by
 * an INTERFACE (the generated-interface idiom) and a controller's implemented interfaces, which are
 * not entities. Rather than widen the entity resolver and risk perturbing its byte-identical behavior,
 * this is a separate index over interfaces too. The scoping steps are identical: a unique simple name
 * resolves directly (the no-collision fast path), else Java's own name resolution against the
 * reference's compilation unit (fully-qualified, single-type import, same package, one wildcard),
 * else the reference is ambiguous or not in the tree and does not resolve. It never guesses a default.
 */
final class TypeScope {

    private final Map<String, List<String>> fqcnsBySimpleName;
    private final Map<String, ClassOrInterfaceDeclaration> classOrInterfaceByFqcn;

    private TypeScope(Map<String, List<String>> fqcnsBySimpleName,
                      Map<String, ClassOrInterfaceDeclaration> classOrInterfaceByFqcn) {
        this.fqcnsBySimpleName = fqcnsBySimpleName;
        this.classOrInterfaceByFqcn = classOrInterfaceByFqcn;
    }

    /** Indexes every class, interface, and enum declared across the units by simple name and FQCN. */
    static TypeScope from(List<CompilationUnit> units) {
        Map<String, List<String>> fqcnsBySimpleName = new HashMap<>();
        Map<String, ClassOrInterfaceDeclaration> classOrInterfaceByFqcn = new HashMap<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                decl.getFullyQualifiedName().ifPresent(fqcn -> {
                    fqcnsBySimpleName.computeIfAbsent(decl.getNameAsString(), k -> new ArrayList<>()).add(fqcn);
                    classOrInterfaceByFqcn.put(fqcn, decl);
                });
            }
            for (EnumDeclaration decl : unit.findAll(EnumDeclaration.class)) {
                decl.getFullyQualifiedName().ifPresent(fqcn ->
                        fqcnsBySimpleName.computeIfAbsent(decl.getNameAsString(), k -> new ArrayList<>()).add(fqcn));
            }
        }
        return new TypeScope(fqcnsBySimpleName, classOrInterfaceByFqcn);
    }

    /**
     * Resolve a type reference, as written in source, to a single in-tree FQCN against its compilation
     * unit. Empty when the reference is not an in-tree type or the scoping cannot narrow it to exactly
     * one (ambiguous), which the caller treats as a refusal, never a guess.
     */
    Optional<String> resolveFqcn(String typeAsString, CompilationUnit cu) {
        String raw = stripGenericsAndArrays(typeAsString);
        String simple = lastSegment(raw);
        List<String> candidates = fqcnsBySimpleName.getOrDefault(simple, List.of());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (raw.contains(".")) {
            return candidates.contains(raw) ? Optional.of(raw) : Optional.empty();
        }
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isAsterisk() && !imp.isStatic() && lastSegment(imp.getNameAsString()).equals(simple)) {
                String imported = imp.getNameAsString();
                return candidates.contains(imported) ? Optional.of(imported) : Optional.empty();
            }
        }
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        String samePackage = pkg.isEmpty() ? simple : pkg + "." + simple;
        if (candidates.contains(samePackage)) {
            return Optional.of(samePackage);
        }
        Set<String> viaWildcard = new LinkedHashSet<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() && !imp.isStatic()) {
                String reached = imp.getNameAsString() + "." + simple;
                if (candidates.contains(reached)) {
                    viaWildcard.add(reached);
                }
            }
        }
        if (viaWildcard.size() == 1) {
            return Optional.of(viaWildcard.iterator().next());
        }
        return Optional.empty();
    }

    /** The class or interface declared at this FQCN, or empty (an enum, or not in the tree). */
    Optional<ClassOrInterfaceDeclaration> classOrInterface(String fqcn) {
        return Optional.ofNullable(classOrInterfaceByFqcn.get(fqcn));
    }

    // Strip a trailing array mark and any generic arguments, leaving the raw (possibly qualified) name.
    private static String stripGenericsAndArrays(String typeAsString) {
        String trimmed = typeAsString.trim();
        int open = trimmed.indexOf('<');
        if (open >= 0) {
            trimmed = trimmed.substring(0, open);
        }
        int bracket = trimmed.indexOf('[');
        if (bracket >= 0) {
            trimmed = trimmed.substring(0, bracket);
        }
        return trimmed.trim();
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
