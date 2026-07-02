package com.tibyaan.core.ir;

import java.util.List;

/**
 * The root of the Capability IR (Volume 4 section 3.1): a neutral, framework free model of
 * what a business system can do.
 *
 * <p>This is the single artifact every provider produces and every backend consumes. It
 * contains no framework, protocol, or language concept. The MVP holds entities and read only
 * capabilities; the future elements in Volume 4 section 7 (workflows, events, policies,
 * permissions, dependencies, domain knowledge) are deliberately absent.
 *
 * @param module       the name of the modeled module or system
 * @param version      the IR schema version
 * @param entities     the business objects, never null
 * @param capabilities the actions the system can perform, never null
 * @param relations    how entities relate, never null
 * @param metadata     free form, non semantic data, never null
 */
public record CapabilityModel(
        String module,
        String version,
        List<Entity> entities,
        List<Capability> capabilities,
        List<Relation> relations,
        Metadata metadata) {

    public CapabilityModel {
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("module must not be null or blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be null or blank");
        }
        entities = entities == null ? List.of() : List.copyOf(entities);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        relations = relations == null ? List.of() : List.copyOf(relations);
        metadata = metadata == null ? Metadata.empty() : metadata;
    }
}
