package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads {@code @Service} classes and catalogs their public methods (Volume 5 section 4.1).
 *
 * <p>This is pure discovery. It produces a neutral list of {@link ServiceMethod} facts and knows
 * nothing about entities, capabilities, or the matching rule. The provider joins this catalog
 * against the entity read intents to discover each capability's binding, which keeps this
 * extractor independent of {@link EntityExtractor}.
 *
 * <p>Only public instance methods on classes annotated {@code @Service} are cataloged. Interfaces,
 * non-service classes, and static or non-public methods are skipped, so a catalog entry is always
 * a real, callable bean method.
 */
public final class ServiceExtractor {

    private static final String SERVICE_ANNOTATION = "Service";

    /** The catalog of public methods on every {@code @Service} class, in a deterministic order. */
    public List<ServiceMethod> discover(List<CompilationUnit> units) {
        return sources(units).stream().map(ServiceMethodSource::method).toList();
    }

    /**
     * The same catalog, each entry carrying its declaration and owning class so the DTO-aware
     * matcher can analyze method bodies (Volume 5 section 4.2). Deterministic order, so the same
     * source always yields the same candidate lists and diagnostics (Volume 5 rule 3).
     */
    public List<ServiceMethodSource> sources(List<CompilationUnit> units) {
        List<ServiceMethodSource> sources = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() || !hasAnnotation(type, SERVICE_ANNOTATION)) {
                    continue;
                }
                String serviceName = type.getNameAsString();
                // Binding Identity (Volume 7 section 12, R1): the binding carries the canonical qualified name,
                // read from the declaration. A local or anonymous class has none and is refused loudly, never
                // bound by its simple name.
                String qualifiedName = type.getFullyQualifiedName().orElseThrow(() -> new IllegalStateException(
                        "@Service '" + serviceName + "' has no qualified name (a local or anonymous class); "
                                + "Binding Identity refuses to bind it by simple name"));
                for (MethodDeclaration method : type.getMethods()) {
                    if (!method.hasModifier(Modifier.Keyword.PUBLIC)
                            || method.hasModifier(Modifier.Keyword.STATIC)) {
                        continue;
                    }
                    sources.add(new ServiceMethodSource(toServiceMethod(serviceName, qualifiedName, method),
                            method, type));
                }
            }
        }
        sources.sort(Comparator
                .comparing((ServiceMethodSource s) -> s.method().serviceSimpleName())
                .thenComparing(s -> s.method().methodName())
                .thenComparing(s -> s.method().signature()));
        return sources;
    }

    private static ServiceMethod toServiceMethod(String serviceName, String qualifiedName,
                                                 MethodDeclaration method) {
        String returnType = simpleName(method.getType().asString());
        List<String> parameterTypes = method.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .toList();
        return new ServiceMethod(serviceName, qualifiedName, method.getNameAsString(), returnType, parameterTypes);
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .anyMatch(annotation -> simpleName.equals(simpleName(annotation.getNameAsString())));
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
