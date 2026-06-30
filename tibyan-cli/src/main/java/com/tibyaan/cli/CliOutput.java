package com.tibyaan.cli;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;

import java.util.List;
import java.util.stream.Collectors;

/** Small shared formatting for the CLI commands. */
final class CliOutput {

    private CliOutput() {
    }

    /** A one-line count of what an IR holds, for example "1 entity (Product), 1 capability (getProduct), 0 relations". */
    static String summary(CapabilityModel ir) {
        int entities = ir.entities().size();
        int capabilities = ir.capabilities().size();
        int relations = ir.relations().size();
        return entities + " " + plural(entities, "entity", "entities")
                + " (" + names(ir.entities().stream().map(Entity::name).collect(Collectors.toList())) + "), "
                + capabilities + " " + plural(capabilities, "capability", "capabilities")
                + " (" + names(ir.capabilities().stream().map(Capability::name).collect(Collectors.toList())) + "), "
                + relations + " " + plural(relations, "relation", "relations");
    }

    static void printValidationFailure(List<String> errors) {
        System.err.println("Validation: FAILED");
        for (String error : errors) {
            System.err.println("  - " + error);
        }
    }

    private static String names(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String plural(int count, String one, String many) {
        return count == 1 ? one : many;
    }
}
