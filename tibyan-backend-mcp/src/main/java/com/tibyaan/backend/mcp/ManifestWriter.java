package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Annotation;
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
        Map<String, Type> inputs = capability.inputs();
        if (inputs.isEmpty()) {
            out.append("      \"inputs\": []\n");
        } else {
            out.append("      \"inputs\": [\n");
            int i = 0;
            for (Map.Entry<String, Type> input : inputs.entrySet()) {
                out.append("        {\n");
                out.append("          \"name\": ").append(quote(input.getKey())).append(",\n");
                out.append("          \"type\": ").append(quote(input.getValue().canonical())).append("\n");
                out.append(i < inputs.size() - 1 ? "        },\n" : "        }\n");
                i++;
            }
            out.append("      ]\n");
        }
        out.append("    }");
    }

    // The IR has no description field, so the backend synthesizes one from the capability's shape.
    // When the read returns a named view, the description says so, since the description is the
    // surface most MCP clients show an agent directly (Volume 6 section 3.1).
    private static String description(Capability capability) {
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
        return viewOf(capability)
                .map(view -> base + " Returns the " + view + " view of " + entity + ".")
                .orElse(base);
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
