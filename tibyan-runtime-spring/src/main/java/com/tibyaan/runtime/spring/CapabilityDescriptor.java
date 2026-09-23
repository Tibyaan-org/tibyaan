package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One tool, as read from the generated capabilities manifest: its name, a description, the
 * opaque binding the runtime resolves, the output type, and the ordered inputs. This is the
 * runtime's view of a capability; it never sees the IR's sealed types, only their string forms.
 *
 * <p>Unknown fields are tolerated explicitly, so the backend can add fields the runtime does not
 * act on (for example {@code view}, the concrete return-shape name, Volume 6 section 3.1) without
 * a runtime change and regardless of the host's Jackson configuration.
 *
 * <p>Phase DL-1 (Volume 7 section 11) reads three ADDITIVE fields, every one absent on a verified
 * and on an attested tool, so every existing manifest deserializes exactly as before:
 * <ul>
 *   <li>{@code kind}: {@code READ}, {@code WRITE} or {@code ACTION}; absent means {@code READ},</li>
 *   <li>{@code tier}: {@code reviewed} on a reviewed tool; absent means verified or attested, and the
 *       {@code attested} flag tells those two apart,</li>
 *   <li>{@code review}: the reviewer's record, present only on a reviewed tool.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilityDescriptor(
        String name,
        String description,
        String binding,
        String output,
        List<InputDescriptor> inputs,
        boolean attested,
        boolean securityUnverifiable,
        boolean attestedBoundary,
        String kind,
        String tier,
        ReviewDescriptor review) {

    /** The kind every discovered and every attested capability has, and the manifest's default. */
    public static final String KIND_READ = "READ";
    /** A reviewed capability the tool labeled WRITE: a mutation was visible (Volume 5 section 4.17.3, RV-H). */
    public static final String KIND_WRITE = "WRITE";
    /** A reviewed capability the tool labeled UNKNOWN: admitted as an action behind the write gate (D4). */
    public static final String KIND_ACTION = "ACTION";

    /** The manifest value of {@code tier} on a reviewed tool. */
    public static final String TIER_REVIEWED = "reviewed";

    public CapabilityDescriptor {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        // Absent means READ (Volume 6 section 3.9). The value is kept as written otherwise: the registrar
        // refuses an unknown kind loudly rather than guessing it is a read.
        kind = (kind == null || kind.isBlank()) ? KIND_READ : kind;
    }

    /**
     * The verified-tool arity (no attestation, no unverifiable marker), for the common case and for
     * existing callers. A verified manifest omits both flags, so they default false.
     */
    public CapabilityDescriptor(String name, String description, String binding, String output,
            List<InputDescriptor> inputs) {
        this(name, description, binding, output, inputs, false, false, false);
    }

    /** The attested-tool arity of 0.3.0, kept for existing callers: kind READ, no tier, no review. */
    public CapabilityDescriptor(String name, String description, String binding, String output,
            List<InputDescriptor> inputs, boolean attested, boolean securityUnverifiable,
            boolean attestedBoundary) {
        this(name, description, binding, output, inputs, attested, securityUnverifiable, attestedBoundary,
                KIND_READ, null, null);
    }

    /**
     * A reviewed tool (tier {@code reviewed}), as the backend writes it: the kind comes from the tool's
     * label, the review carries the named human, and the security marker composes exactly as for an
     * attested tool (Volume 5 section 4.17.6).
     */
    public static CapabilityDescriptor reviewed(String name, String description, String binding, String output,
            List<InputDescriptor> inputs, String kind, boolean securityUnverifiable, ReviewDescriptor review) {
        return new CapabilityDescriptor(name, description, binding, output, inputs, false, securityUnverifiable,
                false, kind, TIER_REVIEWED, review);
    }

    /** Whether this tool is of the reviewed tier: the manifest says so by tier, by review, or by both. */
    public boolean reviewed() {
        return TIER_REVIEWED.equals(tier) || review != null;
    }

    /** Whether this tool is a verified one: neither attested nor reviewed. */
    public boolean verified() {
        return !attested && !reviewed();
    }

    /** Whether this tool's kind is WRITE or ACTION: the two kinds the write gate governs. */
    public boolean mutating() {
        return KIND_WRITE.equals(kind) || KIND_ACTION.equals(kind);
    }

    /** Whether the kind is one of the three the manifest may carry. */
    public boolean hasKnownKind() {
        return KIND_READ.equals(kind) || mutating();
    }
}
