package com.tibyaan.provider.spring.extractors;

import java.util.List;

/**
 * The declared-security state of a capability (Volume 5 section 4.14.5): exactly one of three states,
 * carried onto the capability through the Annotation hook, no IR growth.
 *
 * <ul>
 *   <li>COMPILED: the complete declared authority contract, an any-of sequence of literal strings the
 *       application's security layer matches literally. Carried verbatim, never transformed.
 *   <li>UNVERIFIABLE: a declared security contract EXISTS but the tool could not compile it completely,
 *       with a closed reason token. It must not be treated as carrying no contract.
 *   <li>ABSENT: no method-security declaration was found on the capability's binding or any associated
 *       controller endpoint. NOT a claim the read is unprotected (URL-level security is invisible to
 *       this analysis); it means only that no declared method-level contract was found to carry.
 * </ul>
 */
record SecurityState(State state, List<String> authorities, Reason reason) {

    enum State {
        COMPILED,
        UNVERIFIABLE,
        ABSENT
    }

    /**
     * The closed set of reasons a declared contract could not be compiled (Volume 5 section 4.14.5).
     * Declaration order is the precedence order used when more than one applies across a capability's
     * routes: the most intrinsic per-declaration reason is reported over the cross-route conflict, so
     * the outcome is deterministic (CLAUDE.md rule 10).
     */
    enum Reason {
        RESULT_ANNOTATION("result-annotation"),
        MIXED_MECHANISMS("mixed-mechanisms"),
        ROLE_EXPRESSION("role-expression"),
        UNPARSEABLE_EXPRESSION("unparseable-expression"),
        UNRESOLVABLE_CONSTANT("unresolvable-constant"),
        NO_MODERN_ENABLEMENT("no-modern-enablement"),
        CONFLICTING_DECLARATIONS("conflicting-declarations");

        private final String token;

        Reason(String token) {
            this.token = token;
        }

        String token() {
            return token;
        }
    }

    static SecurityState compiled(List<String> authorities) {
        return new SecurityState(State.COMPILED, List.copyOf(authorities), null);
    }

    static SecurityState unverifiable(Reason reason) {
        return new SecurityState(State.UNVERIFIABLE, List.of(), reason);
    }

    static SecurityState absent() {
        return new SecurityState(State.ABSENT, List.of(), null);
    }

    boolean isCompiled() {
        return state == State.COMPILED;
    }

    boolean isUnverifiable() {
        return state == State.UNVERIFIABLE;
    }
}
