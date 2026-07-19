package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.tibyaan.provider.spring.extractors.SecurityDeclaration.Mechanism;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared reading of method-security annotations (Volume 5 section 4.14.4). Both the controller handler
 * catalog (over method, class, interface method, interface) and the bound-service-method check (over
 * method, class) resolve the effective declaration per mechanism by the framework's most-specific-wins
 * order, so the reading of an annotation's payload lives in one place.
 */
final class SecurityDeclarations {

    private SecurityDeclarations() {
    }

    /**
     * One annotated declaration site, reduced to just what the security reading needs: its annotations,
     * the compilation unit it lives in (for constant scoping), and the type it is on (for a same-class
     * constant). A small concrete record, so no F-bounded {@code NodeWithAnnotations} wildcard reaches a
     * method signature.
     */
    record AnnotatedNode(List<AnnotationExpr> annotations, CompilationUnit cu,
                         ClassOrInterfaceDeclaration declaringType) {

        static AnnotatedNode of(MethodDeclaration method) {
            return new AnnotatedNode(method.getAnnotations(), method.findCompilationUnit().orElse(null),
                    method.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null));
        }

        static AnnotatedNode of(ClassOrInterfaceDeclaration type) {
            return new AnnotatedNode(type.getAnnotations(), type.findCompilationUnit().orElse(null), type);
        }
    }

    /**
     * The effective declaration per mechanism across the given sites, taken in most-specific-first order
     * (Volume 5 section 4.14.4): the first site that declares a mechanism wins for that mechanism, and
     * later sites never override it. One declaration per mechanism at most.
     */
    static List<SecurityDeclaration> effective(List<AnnotatedNode> mostSpecificFirst) {
        Map<Mechanism, SecurityDeclaration> byMechanism = new LinkedHashMap<>();
        for (AnnotatedNode node : mostSpecificFirst) {
            for (AnnotationExpr annotation : node.annotations()) {
                Optional<Mechanism> mechanism = Mechanism.fromAnnotationSimpleName(
                        simpleName(annotation.getNameAsString()));
                if (mechanism.isEmpty() || byMechanism.containsKey(mechanism.get())) {
                    continue;
                }
                byMechanism.put(mechanism.get(), toDeclaration(mechanism.get(), annotation, node));
            }
        }
        return new ArrayList<>(byMechanism.values());
    }

    private static SecurityDeclaration toDeclaration(Mechanism mechanism, AnnotationExpr annotation,
                                                     AnnotatedNode owner) {
        String expression = null;
        List<Expression> values = List.of();
        if (mechanism == Mechanism.PRE_AUTHORIZE || mechanism == Mechanism.POST_AUTHORIZE) {
            expression = singleStringValue(annotation).orElse(null);
        } else if (mechanism == Mechanism.SECURED || mechanism == Mechanism.ROLES_ALLOWED) {
            values = annotationValues(annotation);
        }
        return new SecurityDeclaration(mechanism, expression, values, owner.cu(), owner.declaringType());
    }

    // The single string value of a @PreAuthorize/@PostAuthorize (its SpEL expression), when written as
    // a string literal; empty otherwise, which the compiler treats as unparseable.
    private static Optional<String> singleStringValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single
                && single.getMemberValue() instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value"))
                    .findFirst()
                    .map(pair -> pair.getValue())
                    .filter(value -> value instanceof StringLiteralExpr)
                    .map(value -> ((StringLiteralExpr) value).asString());
        }
        return Optional.empty();
    }

    // The flat list of value expressions of a @Secured/@RolesAllowed: a single member, a value= pair,
    // or an array initializer, normalized to the elements. Each element is a literal or a name reference.
    private static List<Expression> annotationValues(AnnotationExpr annotation) {
        Expression value = null;
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            value = single.getMemberValue();
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            value = normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value"))
                    .findFirst()
                    .map(pair -> pair.getValue())
                    .orElse(null);
        }
        if (value == null) {
            return List.of();
        }
        if (value instanceof ArrayInitializerExpr array) {
            return new ArrayList<>(array.getValues());
        }
        return List.of(value);
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
