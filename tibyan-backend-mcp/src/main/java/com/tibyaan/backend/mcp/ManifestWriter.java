package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes the capabilities manifest the runtime reads: a small, stable JSON describing each
 * generated tool by name, description, opaque binding, output, and ordered inputs. It is written
 * by hand, deterministically, so no JSON library reaches the backend and golden-file output is
 * exact. The runtime parses this manifest to register tools without ever seeing the source.
 */
final class ManifestWriter {

    // The one capability annotation the MCP backend knows how to surface (Volume 6 section 3.1):
    // the concrete view type a read returns, recorded by the provider (Volume 5 section 4.2.5).
    private static final String VIEW_ANNOTATION = "view";

    // The disclosure that a list read's wire is a page envelope (Volume 6 section 3.3; the provider
    // records it on the paged findAll(Pageable) variant, Volume 5 section 4.10.3).
    private static final String PAGED_ANNOTATION = "paged";

    // The declared security contract a read's controller surface carries (Volume 6 section 3.5; the
    // provider compiles it, Volume 5 section 4.14). requiredAuthorities is the complete any-of literal
    // sequence; securityUnverifiable marks a declared contract the tool could not compile. Absent means
    // no declaration was found, which is NOT a claim the read is unprotected (see docs/SECURITY.md).
    private static final String REQUIRED_AUTHORITIES_ANNOTATION = "requiredAuthorities";
    private static final String SECURITY_UNVERIFIABLE_ANNOTATION = "securityUnverifiable";

    private ManifestWriter() {
    }

    static String toJson(CapabilityModel ir) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"module\": ").append(quote(ir.module())).append(",\n");
        out.append("  \"version\": ").append(quote(ir.version())).append(",\n");
        List<Capability> tools = ir.capabilities();
        if (tools.isEmpty()) {
            out.append("  \"tools\": []\n");
        } else {
            out.append("  \"tools\": [\n");
            for (int i = 0; i < tools.size(); i++) {
                appendTool(out, tools.get(i));
                out.append(i < tools.size() - 1 ? ",\n" : "\n");
            }
            out.append("  ]\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static void appendTool(StringBuilder out, Capability capability) {
        out.append("    {\n");
        out.append("      \"name\": ").append(quote(capability.name())).append(",\n");
        out.append("      \"description\": ").append(quote(description(capability))).append(",\n");
        out.append("      \"binding\": ").append(quote(capability.binding().value())).append(",\n");
        out.append("      \"output\": ").append(quote(capability.output().canonical())).append(",\n");
        // A list read refines the output: outputMultiple says the tool returns a collection of the
        // element type, and paged discloses a page-envelope wire (Volume 6 section 3.3). Both are
        // additive and present only for a list read, so every singular capability stays byte-identical.
        if (capability.outputMultiple()) {
            out.append("      \"outputMultiple\": true,\n");
            if (hasAnnotation(capability, PAGED_ANNOTATION)) {
                out.append("      \"paged\": true,\n");
            }
        }
        // The view refines the output: present only when the read returns a named view of the
        // entity (Volume 6 section 3.1). Absent otherwise, so unviewed capabilities are unchanged.
        viewOf(capability).ifPresent(view ->
                out.append("      \"view\": ").append(quote(view)).append(",\n"));
        // The declared security contract (Volume 6 section 3.5), after output and its refinements: a
        // compiled any-of authority array, or the unverifiable marker, or neither (absent). Additive and
        // present only when a contract is declared, so every capability with no contract is byte-identical.
        requiredAuthoritiesOf(capability).ifPresent(authorities -> {
            out.append("      \"requiredAuthorities\": [");
            for (int i = 0; i < authorities.size(); i++) {
                out.append(quote(authorities.get(i)));
                out.append(i < authorities.size() - 1 ? ", " : "");
            }
            out.append("],\n");
        });
        if (isSecurityUnverifiable(capability)) {
            out.append("      \"securityUnverifiable\": true,\n");
        }
        // The attested carriage (Volume 6 section 3.6): the class flag and the attestation object, both
        // character-exact, present only on an attested tool. A verified tool gains neither field, so every
        // existing manifest byte is unchanged (the byte-identity guard, fixture ATT-11).
        if (capability.hasAttestation()) {
            out.append("      \"attested\": true,\n");
            appendAttestation(out, capability.attestation());
        }
        Map<String, Type> inputs = capability.inputs();
        if (inputs.isEmpty()) {
            out.append("      \"inputs\": []\n");
        } else {
            out.append("      \"inputs\": [\n");
            int i = 0;
            for (Map.Entry<String, Type> input : inputs.entrySet()) {
                out.append("        {\n");
                out.append("          \"name\": ").append(quote(input.getKey())).append(",\n");
                out.append("          \"type\": ").append(quote(input.getValue().canonical()));
                // An attested tool carries a per-input description, verbatim: the meaning sentence for a
                // domain filter, the fixed paging description for a paging-marked input, or a structural
                // description for the proven id. No input ever stands in an attested schema with its meaning
                // unstated (the anti-modeled-opaque guard, Volume 6 section 3.6 condition 3).
                if (capability.hasAttestation()) {
                    out.append(",\n          \"description\": ")
                            .append(quote(inputDescription(capability, input.getKey())));
                }
                // Phase Enum Inputs (Volume 6 section 3.7): an input carrying a value constraint renders
                // as the closed string set, in DECLARATION ORDER, verbatim as the provider read it, never
                // sorted, filtered, relabelled, or case-normalised. THE HONESTY GATE: the key is emitted
                // only because the runtime enforces it (EI-R, built 2026-08-05). A schema enum an agent
                // reads as binding, backed by nothing, is the input-side modeled-opaque claim this project
                // permanently rejected. An unconstrained input renders exactly as before.
                List<String> values = capability.inputValues().get(input.getKey());
                if (values != null && !values.isEmpty()) {
                    out.append(",\n          \"enum\": [");
                    for (int v = 0; v < values.size(); v++) {
                        out.append(v > 0 ? ", " : "").append(quote(values.get(v)));
                    }
                    out.append("]");
                }
                out.append("\n").append(i < inputs.size() - 1 ? "        },\n" : "        }\n");
                i++;
            }
            out.append("      ]\n");
        }
        out.append("    }");
    }

    // The attestation object, character-exact from the component's own fields (Volume 6 section 3.6
    // condition 2). The tokens are the canonical lowercase forms an agent reads.
    private static void appendAttestation(StringBuilder out, Attestation attestation) {
        out.append("      \"attestation\": {\n");
        out.append("        \"category\": ").append(quote(attestation.category().token())).append(",\n");
        out.append("        \"resultSemantic\": ").append(quote(attestation.resultSemantic())).append(",\n");
        // The asserted subject entity, present only on a shape claim (AT-2): it IS the claim there, so it
        // is carried character-exactly like every other asserted word. A predicate attestation carries
        // none, so an AT-1 manifest is byte-identical to what it was before AT-2.
        if (attestation.subjectEntity() != null) {
            out.append("        \"subjectEntity\": ").append(quote(attestation.subjectEntity())).append(",\n");
        }
        out.append("        \"assertedBy\": ").append(quote(attestation.assertedBy())).append(",\n");
        out.append("        \"origin\": ").append(quote(attestation.origin().token())).append(",\n");
        out.append("        \"date\": ").append(quote(attestation.date())).append(",\n");
        out.append("        \"fingerprint\": ").append(quote(attestation.fingerprint())).append("\n");
        out.append("      },\n");
    }

    // The per-input description of an attested tool (Volume 6 section 3.6 condition 3).
    private static String inputDescription(Capability capability, String input) {
        String coverage = capability.attestation().parameterMeanings().get(input);
        if (Attestation.PAGING_MARK.equals(coverage)) {
            return "Paging or ordering parameter.";
        }
        if (Attestation.ID_MARK.equals(coverage)) {
            String entity = capability.output() instanceof EntityRef ref
                    ? ref.entityName()
                    : capability.output().canonical();
            return "The " + entity + " identifier.";
        }
        return coverage;
    }

    // The IR has no description field, so the backend synthesizes one from the capability's shape.
    // When the read returns a named view, the description says so, since the description is the
    // surface most MCP clients show an agent directly (Volume 6 section 3.1).
    private static String description(Capability capability) {
        // An attested tool's description LEADS WITH THE CLASS (Volume 6 section 3.6 condition 4): the label
        // is the first sentence because truncation must never strip the epistemic class, then the human's
        // result sentence verbatim, then the provenance. Built only from the attestation's own fields.
        if (capability.hasAttestation()) {
            Attestation attestation = capability.attestation();
            return "Human-attested, not tool-verified. " + attestation.resultSemantic()
                    + " Asserted by " + attestation.assertedBy() + " (" + attestation.origin().token()
                    + "), " + attestation.date() + ".";
        }
        String entity = capability.output() instanceof EntityRef ref
                ? ref.entityName()
                : capability.output().canonical();
        // A list read (Volume 6 section 3.3): "List all Entity.", with a page-envelope disclosure when
        // the read is paged, since the description is the surface most MCP clients show an agent.
        if (capability.outputMultiple()) {
            String base = "List all " + entity + ".";
            return hasAnnotation(capability, PAGED_ANNOTATION)
                    ? base + " Returns a page of results; the total count and page envelope are not modeled."
                    : base;
        }
        String base = capability.inputs().isEmpty()
                ? "Read " + entity + "."
                : "Read " + entity + " by " + String.join(", ", capability.inputs().keySet()) + ".";
        String read = viewOf(capability)
                .map(view -> base + " Returns the " + view + " view of " + entity + ".")
                .orElse(base);
        return read + securityClause(capability);
    }

    // The security clause the description carries (Volume 6 section 3.5), since the description is the
    // surface most MCP clients show an agent directly. A compiled contract lists the any-of authorities;
    // an unverifiable one warns and names the reason; an absent contract adds nothing.
    private static String securityClause(Capability capability) {
        Optional<List<String>> authorities = requiredAuthoritiesOf(capability);
        if (authorities.isPresent()) {
            return " Requires any of the declared authorities: " + String.join(", ", authorities.get()) + ".";
        }
        return securityUnverifiableReason(capability)
                .map(reason -> " Carries a declared security contract that could not be structurally "
                        + "verified (" + reason + "); resolve it before exposing this tool.")
                .orElse("");
    }

    // The compiled any-of authority sequence, when the read carries a fully-parsed contract.
    private static Optional<List<String>> requiredAuthoritiesOf(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(REQUIRED_AUTHORITIES_ANNOTATION)) {
                return Optional.of(annotation.values());
            }
        }
        return Optional.empty();
    }

    private static boolean isSecurityUnverifiable(Capability capability) {
        return securityUnverifiableReason(capability).isPresent();
    }

    private static Optional<String> securityUnverifiableReason(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(SECURITY_UNVERIFIABLE_ANNOTATION) && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
    }

    // The view type simple name, when the capability carries the backend's one known annotation.
    private static Optional<String> viewOf(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(VIEW_ANNOTATION) && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
    }

    private static boolean hasAnnotation(Capability capability, String key) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append("\"").toString();
    }
}
