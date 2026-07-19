package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.List;
import java.util.Optional;

/**
 * One detected method-security annotation (Volume 5 section 4.14.4, the detection set). Detection is
 * total and cheap: the annotation's presence is the fact, whatever its value. Whether it can be
 * COMPILED into a literal authority sequence is {@link AuthorityCompiler}'s job.
 *
 * @param mechanism     which security mechanism this declaration is
 * @param expression    the raw SpEL expression string, for {@code @PreAuthorize}/{@code @PostAuthorize};
 *                      null for the others
 * @param values        the annotation value expressions, for {@code @Secured}/{@code @RolesAllowed};
 *                      empty for the others
 * @param cu            the compilation unit the annotation is written in, for constant scoping
 * @param declaringType the type the annotation is on, for a same-class constant
 */
record SecurityDeclaration(
        Mechanism mechanism,
        String expression,
        List<Expression> values,
        CompilationUnit cu,
        ClassOrInterfaceDeclaration declaringType) {

    /**
     * The recognized method-security mechanisms (Volume 5 section 4.14.4). The jakarta trio
     * {@code @PermitAll}/{@code @DenyAll} join here; {@code @RolesAllowed} is both a jakarta and a
     * legacy Spring name and maps to one mechanism.
     */
    enum Mechanism {
        PRE_AUTHORIZE,
        POST_AUTHORIZE,
        SECURED,
        ROLES_ALLOWED,
        PRE_FILTER,
        POST_FILTER,
        PERMIT_ALL,
        DENY_ALL;

        /** The mechanism an annotation of this simple name declares, or empty if it is not a known one. */
        static Optional<Mechanism> fromAnnotationSimpleName(String simpleName) {
            return switch (simpleName) {
                case "PreAuthorize" -> Optional.of(PRE_AUTHORIZE);
                case "PostAuthorize" -> Optional.of(POST_AUTHORIZE);
                case "Secured" -> Optional.of(SECURED);
                case "RolesAllowed" -> Optional.of(ROLES_ALLOWED);
                case "PreFilter" -> Optional.of(PRE_FILTER);
                case "PostFilter" -> Optional.of(POST_FILTER);
                case "PermitAll" -> Optional.of(PERMIT_ALL);
                case "DenyAll" -> Optional.of(DENY_ALL);
                default -> Optional.empty();
            };
        }
    }
}
