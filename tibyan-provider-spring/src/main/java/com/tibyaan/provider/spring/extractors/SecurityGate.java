package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

import java.util.List;

/**
 * The modern-enablement gate for {@code @Secured} (Volume 5 section 4.14.4, compilation shape b). A
 * global fact, computed once over the scanned source: {@code @Secured} values are matched LITERALLY
 * against granted authorities (the semantics the tool carries) only under {@code
 * @EnableMethodSecurity(securedEnabled = true)}. The legacy {@code @EnableGlobalMethodSecurity} route
 * processes {@code @Secured} through a role-prefix voter whose matching depends on configuration the
 * tool will not interpret, so its presence poisons the gate. The gate passes only when the modern
 * annotation is present in the scanned tree and the legacy one is absent.
 */
final class SecurityGate {

    private final boolean modernSecuredEnabled;
    private final boolean legacyPresent;

    private SecurityGate(boolean modernSecuredEnabled, boolean legacyPresent) {
        this.modernSecuredEnabled = modernSecuredEnabled;
        this.legacyPresent = legacyPresent;
    }

    static SecurityGate from(List<CompilationUnit> units) {
        boolean modern = false;
        boolean legacy = false;
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                for (AnnotationExpr annotation : type.getAnnotations()) {
                    String simple = lastSegment(annotation.getNameAsString());
                    if (simple.equals("EnableGlobalMethodSecurity")) {
                        legacy = true;
                    } else if (simple.equals("EnableMethodSecurity") && securedEnabledTrue(annotation)) {
                        modern = true;
                    }
                }
            }
        }
        return new SecurityGate(modern, legacy);
    }

    /** Whether {@code @Secured} values may be taken as literal authorities (Volume 5 section 4.14.4). */
    boolean passesForSecured() {
        return modernSecuredEnabled && !legacyPresent;
    }

    // @EnableMethodSecurity enables @Secured only when securedEnabled = true is written explicitly; its
    // default is false, so a bare @EnableMethodSecurity does not enable @Secured and does not pass.
    private static boolean securedEnabledTrue(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return false;
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (pair.getNameAsString().equals("securedEnabled")) {
                return pair.getValue().toString().equals("true");
            }
        }
        return false;
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
