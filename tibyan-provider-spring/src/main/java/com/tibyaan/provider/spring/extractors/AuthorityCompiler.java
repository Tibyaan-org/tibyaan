package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.expr.Expression;
import com.tibyaan.provider.spring.extractors.SecurityDeclaration.Mechanism;
import com.tibyaan.provider.spring.extractors.SecurityState.Reason;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the effective method-security declarations of ONE endpoint into a {@link SecurityState}
 * (Volume 5 section 4.14.4). It compiles only the two shapes in the compilation set, a whole-expression
 * {@code hasAuthority}/{@code hasAnyAuthority} of string literals, and a gated {@code @Secured} of
 * literal-resolving constants, and refuses everything else to a named-reason unverifiable state, never a
 * partial authority list. The cross-route composition over a capability's associated endpoints is the
 * provider join's job ({@link ControllerAuthority}); this compiles a single endpoint's declaration set.
 */
final class AuthorityCompiler {

    // The bounded SpEL templates (the P1b token-template discipline applied to SpEL): the tool locates
    // the function name and its single-quoted string literals and nothing else. Anchored end to end, so
    // any trailing clause, operator, variable, or non-literal argument fails the match and is unparseable.
    private static final Pattern HAS_AUTHORITY =
            Pattern.compile("^hasAuthority\\(\\s*'([^']*)'\\s*\\)$");
    private static final Pattern HAS_ANY_AUTHORITY =
            Pattern.compile("^hasAnyAuthority\\(\\s*('[^']*'\\s*(?:,\\s*'[^']*'\\s*)*)\\)$");
    private static final Pattern HAS_ROLE =
            Pattern.compile("^hasRole\\(\\s*'[^']*'\\s*\\)$");
    private static final Pattern HAS_ANY_ROLE =
            Pattern.compile("^hasAnyRole\\(\\s*'[^']*'\\s*(?:,\\s*'[^']*'\\s*)*\\)$");
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    private final SecurityGate gate;
    private final ConstantIndex constants;

    AuthorityCompiler(SecurityGate gate, ConstantIndex constants) {
        this.gate = gate;
        this.constants = constants;
    }

    /**
     * Compile one endpoint's effective declarations. A result-annotation mechanism present anywhere
     * dominates (it is unverifiable whatever else is declared); then more than one mechanism is mixed;
     * then exactly one mechanism is compiled on its own terms.
     */
    SecurityState compile(List<SecurityDeclaration> declarations) {
        if (declarations.stream().anyMatch(AuthorityCompiler::isResultAnnotation)) {
            return SecurityState.unverifiable(Reason.RESULT_ANNOTATION);
        }
        if (declarations.size() > 1) {
            return SecurityState.unverifiable(Reason.MIXED_MECHANISMS);
        }
        return compileSingle(declarations.get(0));
    }

    private SecurityState compileSingle(SecurityDeclaration declaration) {
        return switch (declaration.mechanism()) {
            case PRE_AUTHORIZE -> compileExpression(declaration.expression());
            case SECURED -> compileSecured(declaration);
            case ROLES_ALLOWED -> SecurityState.unverifiable(Reason.ROLE_EXPRESSION);
            case PERMIT_ALL, DENY_ALL -> SecurityState.unverifiable(Reason.UNPARSEABLE_EXPRESSION);
            // Result-annotation mechanisms are handled before the single-declaration path; a
            // @PostAuthorize/@PreFilter/@PostFilter alone still resolves here as result-annotation.
            case POST_AUTHORIZE, PRE_FILTER, POST_FILTER -> SecurityState.unverifiable(Reason.RESULT_ANNOTATION);
        };
    }

    // A @PreAuthorize expression: hasAuthority('X') or hasAnyAuthority('X','Y') compiles to the literal
    // any-of sequence; hasRole/hasAnyRole is a role expression (the framework transforms the string, so
    // the literal is not the matched authority); everything else, hasPermission, a bean call, a #param,
    // a variable, a compound, is unparseable. Never evaluated, only matched as a template.
    private SecurityState compileExpression(String expression) {
        if (expression == null) {
            return SecurityState.unverifiable(Reason.UNPARSEABLE_EXPRESSION);
        }
        String trimmed = expression.trim();
        Matcher one = HAS_AUTHORITY.matcher(trimmed);
        if (one.matches()) {
            return SecurityState.compiled(List.of(one.group(1)));
        }
        Matcher any = HAS_ANY_AUTHORITY.matcher(trimmed);
        if (any.matches()) {
            return SecurityState.compiled(literals(any.group(1)));
        }
        if (HAS_ROLE.matcher(trimmed).matches() || HAS_ANY_ROLE.matcher(trimmed).matches()) {
            return SecurityState.unverifiable(Reason.ROLE_EXPRESSION);
        }
        return SecurityState.unverifiable(Reason.UNPARSEABLE_EXPRESSION);
    }

    // A @Secured declaration: gated on modern enablement, then every value must resolve to a literal
    // (an inline literal, or a constant whose initializer is exactly one string literal). Any value that
    // does not resolve makes the whole declaration unverifiable, never a partial list.
    private SecurityState compileSecured(SecurityDeclaration declaration) {
        if (!gate.passesForSecured()) {
            return SecurityState.unverifiable(Reason.NO_MODERN_ENABLEMENT);
        }
        if (declaration.values().isEmpty()) {
            return SecurityState.unverifiable(Reason.UNRESOLVABLE_CONSTANT);
        }
        List<String> authorities = new ArrayList<>();
        for (Expression value : declaration.values()) {
            var literal = constants.resolveLiteral(value, declaration.cu(), declaration.declaringType());
            if (literal.isEmpty()) {
                return SecurityState.unverifiable(Reason.UNRESOLVABLE_CONSTANT);
            }
            authorities.add(literal.get());
        }
        return SecurityState.compiled(authorities);
    }

    private static boolean isResultAnnotation(SecurityDeclaration declaration) {
        Mechanism mechanism = declaration.mechanism();
        return mechanism == Mechanism.POST_AUTHORIZE
                || mechanism == Mechanism.PRE_FILTER
                || mechanism == Mechanism.POST_FILTER;
    }

    private static List<String> literals(String group) {
        List<String> values = new ArrayList<>();
        Matcher matcher = LITERAL.matcher(group);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
