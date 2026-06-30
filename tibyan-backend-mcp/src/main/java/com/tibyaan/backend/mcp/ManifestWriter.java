package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Type;

import java.util.List;
import java.util.Map;

/**
 * Writes the capabilities manifest the runtime reads: a small, stable JSON describing each
 * generated tool by name, description, opaque binding, output, and ordered inputs. It is written
 * by hand, deterministically, so no JSON library reaches the backend and golden-file output is
 * exact. The runtime parses this manifest to register tools without ever seeing the source.
 */
final class ManifestWriter {

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
    private static String description(Capability capability) {
        String entity = capability.output() instanceof EntityRef ref
                ? ref.entityName()
                : capability.output().canonical();
        if (capability.inputs().isEmpty()) {
            return "Read " + entity + ".";
        }
        return "Read " + entity + " by " + String.join(", ", capability.inputs().keySet()) + ".";
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
