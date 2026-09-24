package com.tibyaan.runtime.spring;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

import java.security.Principal;
import java.util.function.Supplier;

/**
 * Phase SEC-1, rule S2 (Volume 7 section 15): runs an invocation with the captured caller as the executing
 * thread's {@code SecurityContext}, cleared in {@code finally}, so a host that declares method security on a
 * service bean sees the real caller. Referenced only when Spring Security is on the host's classpath
 * ({@link #available()}); the runtime's dependency on it is optional.
 */
final class SecuredInvocation {

    private static final boolean PRESENT = ClassUtils.isPresent(
            "org.springframework.security.core.context.SecurityContextHolder", SecuredInvocation.class.getClassLoader());

    private SecuredInvocation() {
    }

    static boolean available() {
        return PRESENT;
    }

    /**
     * Phase SEC-1, rule S3: the authorities the caller holds, read from a Spring Security {@link Authentication}
     * as the exact granted-authority strings; empty when the principal is not one, because the gate never guesses
     * what a foreign principal may do.
     */
    static java.util.Optional<java.util.Set<String>> authoritiesOf(Principal caller) {
        if (!(caller instanceof Authentication authentication) || authentication.getAuthorities() == null) {
            return java.util.Optional.empty();
        }
        java.util.Set<String> held = new java.util.LinkedHashSet<>();
        authentication.getAuthorities().forEach(granted -> {
            if (granted != null && granted.getAuthority() != null) {
                held.add(granted.getAuthority());
            }
        });
        return java.util.Optional.of(held);
    }

    /** Runs the work with the caller in scope when it is a Spring Security {@link Authentication}; else plainly. */
    static <T> T withCaller(Principal caller, Supplier<T> work) {
        if (!(caller instanceof Authentication authentication)) {
            return work.get();
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.clearContext();
            if (previous != null && previous.getAuthentication() != null) {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
