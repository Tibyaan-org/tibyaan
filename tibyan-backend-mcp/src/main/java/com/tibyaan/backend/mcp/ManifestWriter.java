package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ShapeRef;
import com.tibyaan.core.ir.Shape;
import com.tibyaan.core.ir.Field;
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

    // The three annotations a REVIEWED capability may carry (Volume 5 section 4.17.5; Volume 6 section
    // 3.9), attached by the provider's draft intake and understood one-off here, as every annotation
    // above is. `description` carries the reviewer's sentence verbatim (the words the tool never
    // authors); `void` is a bare flag saying the method returns nothing; `opaqueOutput` names the Java
    // return type as written when the output is not describable (RV-F: the permissive object, never a
    // refusal, because a reviewer who approved the read accepted that shape).
    private static final String DESCRIPTION_ANNOTATION = "description";
    private static final String VOID_ANNOTATION = "void";
    private static final String OPAQUE_OUTPUT_ANNOTATION = "opaqueOutput";

    // The tier token of a reviewed tool. Verified and attested tools carry NO tier field: the reviewed
    // tier is the only one that is not tool-proven, and emitting a token on the proven tiers would change
    // every existing manifest byte (the byte-identity guard, fixture RV-12).
    private static final String REVIEWED_TIER = "reviewed";

    private ManifestWriter() {
    }

    static String toJson(CapabilityModel ir) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"module\": ").append(quote(ir.module())).append(",\n");
        out.append("  \"version\": ").append(quote(ir.version())).append(",\n");
        List<Capability> tools = ir.capabilities();
        Map<String, Shape> shapesByName = new java.util.LinkedHashMap<>();
        for (Shape shape : ir.shapes()) {
            shapesByName.put(shape.name(), shape);
        }
        // Phase DL-2b (Volume 6 section 3.11): the shapes section follows the tools; a manifest without a
        // structured input carries none and is byte-identical.
        boolean hasShapes = !ir.shapes().isEmpty();
        if (tools.isEmpty()) {
            out.append(hasShapes ? "  \"tools\": [],\n" : "  \"tools\": []\n");
        } else {
            out.append("  \"tools\": [\n");
            for (int i = 0; i < tools.size(); i++) {
                appendTool(out, tools.get(i), shapesByName);
                out.append(i < tools.size() - 1 ? ",\n" : "\n");
            }
            out.append(hasShapes ? "  ],\n" : "  ]\n");
        }
        if (hasShapes) {
            appendShapes(out, ir.shapes());
        }
        out.append("}\n");
        return out.toString();
    }

    private static void appendTool(StringBuilder out, Capability capability, Map<String, Shape> shapesByName) {
        out.append("    {\n");
        out.append("      \"name\": ").append(quote(capability.name())).append(",\n");
        out.append("      \"description\": ").append(quote(description(capability, shapesByName))).append(",\n");
        out.append("      \"binding\": ").append(quote(capability.binding().value())).append(",\n");
        // Phase DL-1 (Volume 6 section 3.9): the kind, the tier and the review object, right after the
        // binding. Each is emitted ONLY when it is not the default: `kind` only for WRITE and ACTION (a
        // READ is what every tool was until this phase, so the absence of the field means READ), `tier`
        // and `review` only on a reviewed tool. A verified or attested tool gains none of the three, so
        // every existing manifest byte is unchanged (the byte-identity guard, fixture RV-12). A WRITE or
        // ACTION can only exist with a review (core rule 12), so `kind` never appears without `tier`.
        if (capability.kind() != CapabilityKind.READ) {
            out.append("      \"kind\": ").append(quote(capability.kind().name())).append(",\n");
        }
        if (capability.hasReview()) {
            out.append("      \"tier\": ").append(quote(REVIEWED_TIER)).append(",\n");
            appendReview(out, capability.review());
        }
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
        // A reviewed tool's two output refinements (Volume 5 section 4.17.3, RV-F), the machine half of a
        // disclosure the description also carries in prose, the two-audiences discipline of Volume 6
        // section 4.1 condition 6. `void` says the tool returns nothing, so the `output` token above is
        // the IR's placeholder and not a result shape. `opaqueOutput` names the Java return type whose
        // shape the IR does not model, exactly as `view` names a concrete return shape the IR does not
        // enumerate (section 3.1): the partiality rule, reused rather than re-decided. Both are present
        // only on a reviewed tool that carries the annotation, so every other tool is byte-identical.
        if (hasAnnotation(capability, VOID_ANNOTATION)) {
            out.append("      \"void\": true,\n");
        }
        opaqueOutputOf(capability).ifPresent(type ->
                out.append("      \"opaqueOutput\": ").append(quote(type)).append(",\n"));
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
            // Guard 4 (Volume 5 section 4.15.2.3): the boundary marker rides ALONGSIDE the attested marker,
            // never instead of it, so a consumer filtering on either one behaves correctly.
            if (capability.attestation().hasBoundaryClass()) {
                out.append("      \"attestedBoundary\": true,\n");
            }
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
                // Phase DL-2a (Volume 6 section 3.11): a multiple input keeps its element type and says the
                // wire carries an array of it; an input that is not multiple renders exactly as before.
                if (capability.multipleInputs().contains(input.getKey())) {
                    out.append(",\n          \"multiple\": true");
                }
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

    // Phase DL-2b (Volume 6 section 3.11): the reviewer's sentence is followed by one sentence per structured
    // input naming the shape, its field count and its skipped fields, so the honesty the reviewer approved
    // reaches the agent through the surface most clients show.
    private static String shapeRemark(Capability capability, Map<String, Shape> shapesByName) {
        StringBuilder remark = new StringBuilder();
        for (Map.Entry<String, Type> input : capability.inputs().entrySet()) {
            if (input.getValue() instanceof ShapeRef ref) {
                Shape shape = shapesByName.get(ref.shapeName());
                if (shape == null) {
                    continue;
                }
                List<String> partial = shape.partialFields();
                remark.append(" Input '").append(input.getKey()).append("' is an object of shape ")
                        .append(shape.name()).append(" (").append(shape.fields().size()).append(" field")
                        .append(shape.fields().size() == 1 ? "" : "s");
                if (!partial.isEmpty()) {
                    remark.append("; ").append(partial.size()).append(" not modeled: ")
                            .append(String.join(", ", partial));
                }
                remark.append(").");
            }
        }
        return remark.toString();
    }

    // The shapes section (Volume 6 section 3.11): every shape the model carries, once, with its fields in
    // declaration order, each field's element type, whether the wire carries an array, its closed set, and
    // the partial disclosure. Absent from a manifest without a structured input.
    private static void appendShapes(StringBuilder out, List<Shape> shapes) {
        out.append("  \"shapes\": [\n");
        for (int s = 0; s < shapes.size(); s++) {
            Shape shape = shapes.get(s);
            out.append("    {\n");
            out.append("      \"key\": ").append(quote(shape.key())).append(",\n");
            out.append("      \"name\": ").append(quote(shape.name())).append(",\n");
            out.append("      \"fields\": [\n");
            for (int f = 0; f < shape.fields().size(); f++) {
                Field field = shape.fields().get(f);
                out.append("        {\n");
                out.append("          \"name\": ").append(quote(field.name())).append(",\n");
                out.append("          \"type\": ").append(quote(field.type().canonical()));
                if (shape.multipleFields().contains(field.name())) {
                    out.append(",\n          \"multiple\": true");
                }
                List<String> values = shape.fieldValues().get(field.name());
                if (values != null && !values.isEmpty()) {
                    out.append(",\n          \"enum\": [");
                    for (int v = 0; v < values.size(); v++) {
                        out.append(v > 0 ? ", " : "").append(quote(values.get(v)));
                    }
                    out.append("]");
                }
                out.append("\n").append(f < shape.fields().size() - 1 ? "        },\n" : "        }\n");
            }
            out.append("      ]");
            List<String> partial = shape.partialFields();
            if (!partial.isEmpty()) {
                out.append(",\n      \"partial\": [");
                for (int p = 0; p < partial.size(); p++) {
                    out.append(p > 0 ? ", " : "").append(quote(partial.get(p)));
                }
                out.append("]");
            }
            out.append("\n").append(s < shapes.size() - 1 ? "    },\n" : "    }\n");
        }
        out.append("  ]\n");
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
        // AT-3: the security fields, present only on a boundary attestation. assertedAuthorities is
        // rendered under its OWN name and never as requiredAuthorities, and the securityUnverifiable
        // marker the capability may carry is untouched: an asserted authority is not a compiled one.
        if (attestation.hasBoundaryClass()) {
            out.append("        \"securityOwner\": ").append(quote(attestation.securityOwner()))
                    .append(",\n");
            out.append("        \"readOnlyAffirmed\": ").append(attestation.readOnlyAffirmed())
                    .append(",\n");
            if (!attestation.assertedAuthorities().isEmpty()) {
                out.append("        \"assertedAuthorities\": [");
                List<String> authorities = attestation.assertedAuthorities();
                for (int a = 0; a < authorities.size(); a++) {
                    out.append(a > 0 ? ", " : "").append(quote(authorities.get(a)));
                }
                out.append("],\n");
            }
        }
        out.append("        \"fingerprint\": ").append(quote(attestation.fingerprint())).append("\n");
        out.append("      },\n");
    }

    // The review object (Volume 6 section 3.9), character-exact from the component's own fields: the
    // tool's label beside the human's name, date and the origin of the words, so neither is mistaken for
    // the other. The fingerprint is the intake's pin (RV-B) and stays off the surface, as the design lists
    // the object without it. The label token is the closed-set constant name (READ, WRITE, UNKNOWN).
    private static void appendReview(StringBuilder out, Review review) {
        out.append("      \"review\": {\n");
        out.append("        \"label\": ").append(quote(review.label().name())).append(",\n");
        out.append("        \"reviewedBy\": ").append(quote(review.reviewedBy())).append(",\n");
        out.append("        \"reviewedOn\": ").append(quote(review.reviewedOn())).append(",\n");
        out.append("        \"descriptionOrigin\": ").append(quote(review.descriptionOrigin())).append("\n");
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
    private static String description(Capability capability, Map<String, Shape> shapesByName) {
        // A reviewed tool's description leads with its class too (Volume 6 section 3.9): the first sentence
        // names the reviewer, the origin of the words and the tool's own label, so truncation can never
        // strip the fact that this tool is a named human's decision and not a proof. Then the reviewer's
        // sentence verbatim, then the output remark where the output is not a result shape, then the
        // declared security clause (section 4.17.6: composition runs for reviewed capabilities too).
        if (capability.hasReview()) {
            Review review = capability.review();
            String lead = "Reviewed by " + review.reviewedBy() + " (" + review.descriptionOrigin()
                    + "), not tool-verified; the tool labeled it " + review.label().name() + ".";
            String sentence = reviewerDescription(capability)
                    .map(words -> lead + " " + words)
                    .orElse(lead);
            return sentence + shapeRemark(capability, shapesByName) + outputRemark(capability)
                    + securityClause(capability);
        }
        // An attested tool's description LEADS WITH THE CLASS (Volume 6 section 3.6 condition 4): the label
        // is the first sentence because truncation must never strip the epistemic class, then the human's
        // result sentence verbatim, then the provenance. Built only from the attestation's own fields.
        if (capability.hasAttestation()) {
            Attestation attestation = capability.attestation();
            // AT-3: a boundary tool leads with the STRONGER label, because its class is not "a human
            // described this data" but "a human took responsibility for a security property the tool
            // could not check". The security owner is named in the same sentence that names the asserter.
            if (attestation.hasBoundaryClass()) {
                return "Human-attested BOUNDARY, not tool-verified: its read-only-ness or its security "
                        + "contract rests on a human assertion, not on structure. "
                        + attestation.resultSemantic()
                        + " Asserted by " + attestation.assertedBy() + " (" + attestation.origin().token()
                        + "), " + attestation.date() + ", security owner " + attestation.securityOwner()
                        + ".";
            }
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

    // The prose half of a reviewed tool's output disclosure: a void tool says it returns nothing, an
    // opaque-output tool names the type whose shape is not modeled. Empty for a describable output.
    private static String outputRemark(Capability capability) {
        if (hasAnnotation(capability, VOID_ANNOTATION)) {
            return " (returns nothing)";
        }
        return opaqueOutputOf(capability)
                .map(type -> " Returns " + type + "; its field shape is not modeled.")
                .orElse("");
    }

    // The reviewer's sentence, verbatim, from the description annotation the provider attaches. Absent
    // when the provider attached none, in which case the class sentence stands alone rather than the
    // backend inventing words: the tool never authors a description (Volume 5 section 4.17.8).
    private static Optional<String> reviewerDescription(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(DESCRIPTION_ANNOTATION) && !annotation.values().isEmpty()
                    && !annotation.values().get(0).isBlank()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
    }

    // The Java return type as written, when the reviewed output is not describable.
    private static Optional<String> opaqueOutputOf(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(OPAQUE_OUTPUT_ANNOTATION) && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
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
