package com.tibyaan.provider.spring;

/**
 * A collection-returning {@code @Service} method that E4 does not model, named in the survey with the
 * reason it is deferred (Volume 5 section 4.10.6; Volume 11 section 3.3). Filtered reads, view-returning
 * and projection lists, and non-framework or transformed terminals are all deferred by name to the
 * successor filter-semantics phase; they are never failures of this phase and never counted as missed
 * capabilities, exactly as section 4.2's non-primary-key reads are named rather than failed.
 *
 * @param method the method signature, for example {@code ContactServiceImpl#findByCategory(UUID, Pageable)}
 * @param reason why it is deferred, in the rule's terms
 */
public record DeferredListRead(String method, String reason) {

    public DeferredListRead {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("a deferred list read must name its method");
        }
        reason = reason == null ? "" : reason;
    }

    /** A one-line form for the survey, {@code <method>: <reason>}. */
    public String describe() {
        return method + ": " + reason;
    }
}
