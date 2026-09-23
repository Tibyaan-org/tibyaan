package com.tibyaan.runtime.spring;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The runtime's exposure controls, bound once at startup (Volume 5 section 4.15.7; Volume 7 sections 10
 * and 11). Six properties, three tiers:
 *
 * <ul>
 *   <li>{@code tibyan.attested.expose} (default true): whether attested tools register at all.</li>
 *   <li>{@code tibyan.attested.exposeSecurityUnverifiable} (default false): the GLOBAL switch that opens
 *       every security-unverifiable tool at once. Kept unchanged in meaning and default (section 10 D4); it
 *       now earns a WARN naming every tool it opened that nobody listed.</li>
 *   <li>{@code tibyan.attested.exposeBoundary} (default false): whether a boundary tool registers.</li>
 *   <li>{@code tibyan.reviewed.expose} (default true): whether reviewed tools register at all (DL-1 D2:
 *       the committed approvals are the opt-in, as for attested).</li>
 *   <li>{@code tibyan.writes.allow} (default empty): the write gate, a list of EXACT names of reviewed
 *       WRITE and ACTION tools that may register.</li>
 *   <li>{@code tibyan.security.exposeUnverifiable} (default empty): a list of EXACT names of
 *       security-unverifiable tools, of ANY tier, opened by review (section 10 as amended by DL-1 D1).</li>
 * </ul>
 *
 * <p>The two lists are bound through the Spring {@link Binder}, not {@code @Value}, so the comma-separated
 * property form and the YAML list form bind to the same list (section 10.7, fixture RX-10). Both lists are
 * validated against the loaded manifest under conditions X1, X2, X3 and X5 of section 10.3 by
 * {@link #validate(CapabilitiesManifest)}, which FAILS STARTUP on a violation (D1); X4, that a list never
 * widens another control, is a property of the gate in {@link ToolRegistrar}.
 */
public record ExposurePolicy(
        boolean exposeAttested,
        boolean exposeSecurityUnverifiable,
        boolean exposeBoundary,
        boolean exposeReviewed,
        List<String> writesAllow,
        List<String> exposeUnverifiable) {

    public static final String ATTESTED_EXPOSE = "tibyan.attested.expose";
    public static final String ATTESTED_EXPOSE_SECURITY_UNVERIFIABLE = "tibyan.attested.exposeSecurityUnverifiable";
    public static final String ATTESTED_EXPOSE_BOUNDARY = "tibyan.attested.exposeBoundary";
    public static final String REVIEWED_EXPOSE = "tibyan.reviewed.expose";
    public static final String WRITES_ALLOW = "tibyan.writes.allow";
    public static final String SECURITY_EXPOSE_UNVERIFIABLE = "tibyan.security.exposeUnverifiable";

    public ExposurePolicy {
        // The raw entries are kept, blanks and repeats included, because validate() must see them.
        writesAllow = writesAllow == null ? List.of() : List.copyOf(writesAllow);
        exposeUnverifiable = exposeUnverifiable == null ? List.of() : List.copyOf(exposeUnverifiable);
    }

    /** The defaults: attested open, security-unverifiable closed, boundary closed, reviewed open, both lists empty. */
    public static ExposurePolicy defaults() {
        return new ExposurePolicy(true, false, false, true, List.of(), List.of());
    }

    /**
     * Binds the six properties from the environment. The names are given to the Binder in their canonical
     * kebab-case form, which relaxed binding matches to the camelCase spelling the documentation uses, to
     * the same spelling in YAML, and to the upper-case environment-variable form.
     */
    public static ExposurePolicy bind(Environment environment) {
        Binder binder = Binder.get(environment);
        return new ExposurePolicy(
                binder.bind("tibyan.attested.expose", Boolean.class).orElse(true),
                binder.bind("tibyan.attested.expose-security-unverifiable", Boolean.class).orElse(false),
                binder.bind("tibyan.attested.expose-boundary", Boolean.class).orElse(false),
                binder.bind("tibyan.reviewed.expose", Boolean.class).orElse(true),
                binder.bind("tibyan.writes.allow", Bindable.listOf(String.class)).orElse(List.of()),
                binder.bind("tibyan.security.expose-unverifiable", Bindable.listOf(String.class)).orElse(List.of()));
    }

    /** Whether the write gate names this exact capability. */
    public boolean allowsWrite(String name) {
        return writesAllow.contains(name);
    }

    /** Whether the review list names this exact security-unverifiable capability. */
    public boolean listsUnverifiable(String name) {
        return exposeUnverifiable.contains(name);
    }

    /**
     * Whether a security-unverifiable tool may register: EITHER the global switch is on OR its exact name
     * is listed (section 10.3, the rule). The same rule serves attested and reviewed tools (DL-1 D1).
     */
    public boolean opensUnverifiable(String name) {
        return exposeSecurityUnverifiable || listsUnverifiable(name);
    }

    /**
     * Conditions X1, X2, X3 and X5 (Volume 7 section 10.3) on both lists, against the loaded manifest.
     *
     * @throws IllegalStateException naming the property and the entry on the first violation; the host
     *                               does not start (decision D1)
     */
    public void validate(CapabilitiesManifest manifest) {
        validateList(WRITES_ALLOW, writesAllow, manifest, ExposurePolicy::writeGateNeeds);
        validateList(SECURITY_EXPOSE_UNVERIFIABLE, exposeUnverifiable, manifest, ExposurePolicy::reviewListNeeds);
    }

    private interface Need {
        /** Null when the entry is needed, otherwise why it is not (X3). */
        String notNeeded(CapabilityDescriptor descriptor);
    }

    private static void validateList(String property, List<String> entries, CapabilitiesManifest manifest,
            Need need) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            // X5: no blanks, no duplicates. Checked first because a blank resolves to nothing by definition.
            if (entry == null || entry.isBlank()) {
                throw new IllegalStateException(property + ": entry " + (i + 1) + " is blank; the list is a set "
                        + "of exact capability names, one deliberate act each (condition X5)");
            }
            if (!seen.add(entry)) {
                throw new IllegalStateException(property + ": entry '" + entry + "' is listed more than once; "
                        + "the list is a set of exact capability names, one deliberate act each (condition X5)");
            }
            // X1 and X2: exact, whole, case-sensitive match, or the entry is unknown. A pattern, a prefix and
            // a wrong-case spelling are all unknown names: the only thing that resolves is the name itself.
            CapabilityDescriptor descriptor = manifest.tools().stream()
                    .filter(tool -> entry.equals(tool.name()))
                    .findFirst()
                    .orElse(null);
            if (descriptor == null) {
                throw new IllegalStateException(property + ": entry '" + entry + "' names no capability in the "
                        + "loaded manifest" + unknownHint(entry, manifest) + " (conditions X1 and X2: exact, "
                        + "whole, case-sensitive names only; no wildcard, prefix or pattern)");
            }
            // X3: every entry must be needed. An inert entry records a stake that does not exist.
            String notNeeded = need.notNeeded(descriptor);
            if (notNeeded != null) {
                throw new IllegalStateException(property + ": entry '" + entry + "' " + notNeeded
                        + ", so it registers without the list and the entry records a stake that does not "
                        + "exist (condition X3)");
            }
        }
    }

    // The write gate opens WRITE and ACTION tools and nothing else.
    private static String writeGateNeeds(CapabilityDescriptor descriptor) {
        if (!descriptor.mutating()) {
            return "is kind " + descriptor.kind() + ", not WRITE or ACTION";
        }
        return null;
    }

    // The review list opens security-unverifiable attested or reviewed tools and nothing else.
    private static String reviewListNeeds(CapabilityDescriptor descriptor) {
        if (descriptor.verified()) {
            return "is a verified capability";
        }
        if (!descriptor.securityUnverifiable()) {
            return "is " + (descriptor.attested() ? "attested" : "reviewed")
                    + " but not security-unverifiable";
        }
        return null;
    }

    // A located hint for the operator; the outcome is the same refusal either way.
    private static String unknownHint(String entry, CapabilitiesManifest manifest) {
        if (entry.contains("*")) {
            return "; patterns are not supported";
        }
        List<String> similar = new ArrayList<>();
        for (CapabilityDescriptor tool : manifest.tools()) {
            if (tool.name().equalsIgnoreCase(entry) || tool.name().startsWith(entry)) {
                similar.add(tool.name());
            }
        }
        return similar.isEmpty() ? "" : "; did you mean " + similar + "?";
    }
}
