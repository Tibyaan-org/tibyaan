package com.tibyaan.provider.spring.extractors;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Relation;

import java.util.List;

/**
 * What a single {@link Extractor} contributes to the model: zero or more entities, capabilities,
 * and relations. The provider concatenates the contributions of all enabled extractors.
 *
 * @param entities     entities found by the extractor, never null
 * @param capabilities capabilities found by the extractor, never null
 * @param relations    relations found by the extractor, never null
 */
public record ExtractorContribution(
        List<Entity> entities,
        List<Capability> capabilities,
        List<Relation> relations) {

    public ExtractorContribution {
        entities = entities == null ? List.of() : List.copyOf(entities);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** A contribution that adds nothing, used by placeholder extractors. */
    public static ExtractorContribution empty() {
        return new ExtractorContribution(List.of(), List.of(), List.of());
    }
}
