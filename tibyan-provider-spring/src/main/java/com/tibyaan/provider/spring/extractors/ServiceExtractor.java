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
        List<ServiceMethod> methods = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() || !hasAnnotation(type, SERVICE_ANNOTATION)) {
                    continue;
                }
                String serviceName = type.getNameAsString();
                for (MethodDeclaration method : type.getMethods()) {
                    if (!method.hasModifier(Modifier.Keyword.PUBLIC)
                            || method.hasModifier(Modifier.Keyword.STATIC)) {
                        continue;
                    }
                    methods.add(toServiceMethod(serviceName, method));
                }
            }
        }
        // Sorted so the same source always yields the same catalog and the same diagnostics
        // (Volume 5 rule 3).
        methods.sort(Comparator.comparing(ServiceMethod::serviceSimpleName)
                .thenComparing(ServiceMethod::methodName)
                .thenComparing(ServiceMethod::signature));
        return methods;
    }

    private static ServiceMethod toServiceMethod(String serviceName, MethodDeclaration method) {
        String returnType = simpleName(method.getType().asString());
        List<String> parameterTypes = method.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .toList();
        return new ServiceMethod(serviceName, method.getNameAsString(), returnType, parameterTypes);
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
