package com.tibyaan.runtime.spring;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

/**
 * Phase SEC-1, rules S3 and S4 (Volume 7 section 15.4): the decision taken at the tool boundary, before any
 * invocation, from the tool's manifest entry and the caller the host authenticated.
 *
 * <ul>
 *   <li>S3, the compiled contract: a tool whose entry carries {@code requiredAuthorities} refuses unless the
 *       caller is authenticated and holds at least one of them as a granted authority, compared exactly,
 *       literal for literal (decision D2: no {@code ROLE_} normalization, the contract is what the application
 *       declared). The refusal names the tool and the contract, never the caller's authorities. This holds
 *       whatever {@code requireCaller} says: a contract admits nobody it cannot check.</li>
 *   <li>S4, anonymous: under {@code tibyan.security.requireCaller} (default true) an unauthenticated call to a
 *       reviewed WRITE or ACTION is refused.</li>
 * </ul>
 *
 * <p>A security-unverifiable tool carries no contract by definition, so this gate does nothing to it beyond
 * S4: sections 10 and 11 stay its only gate (SC-8). A tool with neither contract nor mutating kind keeps
 * today's behavior. The gate reads the caller's authorities through {@link SecuredInvocation} only, so it
 * touches Spring Security only when the host has it.
 */
final class CallerGate {

    private CallerGate() {
    }

    /**
     * @return the located refusal, or empty when the call may proceed
     */
    static Optional<String> refusal(CapabilityDescriptor descriptor, Optional<Principal> caller,
                                    boolean requireCaller) {
        if (descriptor.hasContract()) {
            String contract = descriptor.requiredAuthorities().toString();
            if (caller.isEmpty()) {
                return Optional.of("Tibyaan: tool '" + descriptor.name() + "' refused: no authenticated caller, and "
                        + "the tool carries a compiled authority contract " + contract
                        + " (any of; Volume 5 section 4.14)");
            }
            Optional<Set<String>> held = SecuredInvocation.available()
                    ? SecuredInvocation.authoritiesOf(caller.get())
                    : Optional.empty();
            if (held.isEmpty()) {
                return Optional.of("Tibyaan: tool '" + descriptor.name() + "' refused: the caller's authorities "
                        + "cannot be read (the principal is not a Spring Security Authentication), and the tool "
                        + "carries a compiled authority contract " + contract);
            }
            boolean holdsOne = descriptor.requiredAuthorities().stream().anyMatch(held.get()::contains);
            if (!holdsOne) {
                return Optional.of("Tibyaan: tool '" + descriptor.name() + "' refused: the caller holds none of the "
                        + "compiled authorities " + contract + " (any of, compared exactly)");
            }
            return Optional.empty();
        }
        if (requireCaller && descriptor.mutating() && caller.isEmpty()) {
            return Optional.of("Tibyaan: tool '" + descriptor.name() + "' refused: no authenticated caller, and the "
                    + "tool is a reviewed " + descriptor.kind() + " (" + ExposurePolicy.SECURITY_REQUIRE_CALLER
                    + "=true; set it false only for a host with no caller to capture)");
        }
        return Optional.empty();
    }
}
