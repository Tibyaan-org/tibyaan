package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The two-stage key resolver (Volume 5 section 4.12.4). Once entities are keyed by fully-qualified
 * class name (section 4.12.2), every place the provider ties a Java type to an entity must resolve
 * to the entity's KEY, not to a simple-name string match, or a colliding simple name would either
 * fail to match its own entity or match both, a silent mis-bind. This is that resolver.
 *
 * <p>Stage one is a type-agnostic scoping step that follows Java's own name resolution against the
 * reference's compilation unit: a fully-qualified reference resolves directly; else a single-type
 * import; else a type in the same package; else exactly one on-demand (wildcard) import that reaches
 * an in-tree type; else the reference is ambiguous and the resolver refuses, naming the candidates.
 * It never picks a default. Stage two is the caller's: match the resolved FQCN against the
 * declaration set it needs (an entity by key, an enum declaration by FQCN).
 *
 * <p>The unique-simple-name fast path is the no-regression clause (section 4.12.3, condition 1): when
 * a simple name is declared by exactly one in-tree type, it resolves to that type without consulting
 * imports, so an application with no colliding simple names resolves exactly as before and generates
 * byte-identical output. The scoping machinery engages only on an actual collision.
 */
public final class TypeReferenceResolver {

    /** What a resolved FQCN is, in the scanned source. */
    public enum Kind {
        /** A class annotated {@code @Entity}. */
        ENTITY,
        /** An {@code enum} declaration. */
        ENUM,
        /** Any other class or interface. */
        CLASS
    }

    /**
     * The outcome of resolving a type reference. Exactly one of three states: resolved to a single
     * in-tree FQCN, not an in-tree type at all (a library type), or ambiguous across two or more
     * in-tree types the scoping could not narrow to one.
     *
     * @param fqcn       the resolved fully-qualified name, or null when unresolved
     * @param kind       the resolved type's kind, or null when unresolved
     * @param candidates the in-tree FQCNs of that simple name, for a located ambiguity message
     */
    public record Resolution(String fqcn, Kind kind, List<String> candidates) {

        public boolean isResolved() {
            return fqcn != null;
        }

        /** Ambiguous: two or more in-tree types share the simple name and scoping did not narrow it. */
        public boolean isAmbiguous() {
            return fqcn == null && !candidates.isEmpty();
        }

        /** Not in the scanned tree: no in-tree type of that simple name is in scope (a library type). */
        public boolean isNotInTree() {
            return fqcn == null && candidates.isEmpty();
        }

        static Resolution resolved(String fqcn, Kind kind) {
            return new Resolution(fqcn, kind, List.of());
        }

        static Resolution ambiguous(List<String> candidates) {
            return new Resolution(null, null, List.copyOf(candidates));
        }

        static Resolution notInTree() {
            return new Resolution(null, null, List.of());
        }
    }

    private static final String ENTITY_ANNOTATION = "Entity";

    private final Map<String, Kind> kindByFqcn;
    private final Map<String, List<String>> enumConstantsByFqcn;
    private final Map<String, List<String>> fqcnsBySimpleName;

    private TypeReferenceResolver(Map<String, Kind> kindByFqcn,
                                  Map<String, List<String>> enumConstantsByFqcn,
                                  Map<String, List<String>> fqcnsBySimpleName) {
        this.kindByFqcn = kindByFqcn;
        this.enumConstantsByFqcn = enumConstantsByFqcn;
        this.fqcnsBySimpleName = fqcnsBySimpleName;
    }

    /** Builds the resolver by indexing every class, interface, and enum declared across the units. */
    public static TypeReferenceResolver from(List<CompilationUnit> units) {
        Map<String, Kind> kindByFqcn = new HashMap<>();
        Map<String, List<String>> enumConstantsByFqcn = new HashMap<>();
        Map<String, List<String>> fqcnsBySimpleName = new HashMap<>();

        for (CompilationUnit unit : units) {
            for (EnumDeclaration decl : unit.findAll(EnumDeclaration.class)) {
                decl.getFullyQualifiedName().ifPresent(fqcn -> {
                    kindByFqcn.put(fqcn, Kind.ENUM);
                    enumConstantsByFqcn.put(fqcn, constantNames(decl));
                    fqcnsBySimpleName.computeIfAbsent(decl.getNameAsString(), k -> new ArrayList<>()).add(fqcn);
                });
            }
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface()) {
                    continue;
                }
                decl.getFullyQualifiedName().ifPresent(fqcn -> {
                    kindByFqcn.put(fqcn, hasAnnotation(decl, ENTITY_ANNOTATION) ? Kind.ENTITY : Kind.CLASS);
                    fqcnsBySimpleName.computeIfAbsent(decl.getNameAsString(), k -> new ArrayList<>()).add(fqcn);
                });
            }
        }
        return new TypeReferenceResolver(kindByFqcn, enumConstantsByFqcn, fqcnsBySimpleName);
    }

    /**
     * Resolve a Java type reference, as written in source, against its compilation unit (section
     * 4.12.4, stage one). The type string may carry generics or an array mark, which are stripped to
     * the referenced simple (or qualified) name first.
     */
    public Resolution resolve(String typeAsString, CompilationUnit cu) {
        String raw = stripGenericsAndArrays(typeAsString);
        String simple = lastSegment(raw);
        List<String> candidates = fqcnsBySimpleName.getOrDefault(simple, List.of());

        if (candidates.isEmpty()) {
            return Resolution.notInTree();
        }
        // No-regression fast path (section 4.12.3, condition 1): a unique simple name is the type,
        // resolved without consulting imports. This is what keeps a no-collision app byte-identical.
        if (candidates.size() == 1) {
            return resolvedOf(candidates.get(0));
        }

        // A collision. Follow Java's own scoping against the reference's compilation unit.
        // Step 0: the reference is written fully qualified.
        if (raw.contains(".")) {
            return candidates.contains(raw) ? resolvedOf(raw) : Resolution.notInTree();
        }
        // Step a: a single-type import decides, whether it names an in-tree or an external type.
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isAsterisk() && !imp.isStatic() && lastSegment(imp.getNameAsString()).equals(simple)) {
                String imported = imp.getNameAsString();
                return candidates.contains(imported) ? resolvedOf(imported) : Resolution.notInTree();
            }
        }
        // Step b: a type of that simple name declared in the reference's own package.
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        String samePackage = pkg.isEmpty() ? simple : pkg + "." + simple;
        if (candidates.contains(samePackage)) {
            return resolvedOf(samePackage);
        }
        // Step c: exactly one on-demand (wildcard) import that reaches an in-tree type of that name.
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
            return resolvedOf(viaWildcard.iterator().next());
        }
        // Step d: ambiguous. Two or more in-tree types are in scope, or none is: refuse, never guess.
        return Resolution.ambiguous(candidates);
    }

    /** Resolve a reference and, when it resolves to an {@code @Entity}, return its key. */
    public Optional<String> resolveEntityKey(String typeAsString, CompilationUnit cu) {
        Resolution resolution = resolve(typeAsString, cu);
        if (resolution.isResolved() && resolution.kind() == Kind.ENTITY) {
            return Optional.of(resolution.fqcn());
        }
        return Optional.empty();
    }

    /** The constant names of the enum at this FQCN, in declaration order, or empty. */
    public List<String> enumConstants(String fqcn) {
        return enumConstantsByFqcn.getOrDefault(fqcn, List.of());
    }

    private Resolution resolvedOf(String fqcn) {
        return Resolution.resolved(fqcn, kindByFqcn.get(fqcn));
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
