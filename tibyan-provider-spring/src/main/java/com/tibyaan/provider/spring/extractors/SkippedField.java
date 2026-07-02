package com.tibyaan.provider.spring.extractors;

/**
 * One field left out of an entity's IR shape, with a located reason (partial-model integrity,
 * Volume 5 sections 4.8 and 4.7). A field is skipped either by tolerance during extraction (a type
 * the closed set and the association and enum rules cannot map, section 4.8) or dropped by model
 * closure (a reference to an entity absent from the model, section 4.7).
 *
 * <p>Skipping is never silent: every skip is disclosed here, the field name feeds the entity's
 * {@code partial} annotation (section 4.9), and the note appears in the survey. The entity extracts
 * without the field; its shape is explicitly partial, never wrong.
 *
 * @param entity the entity the field belongs to
 * @param field  the field name that was skipped or dropped
 * @param note   the located reason, for the survey
 */
public record SkippedField(String entity, String field, String note) {

    public SkippedField {
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("a skipped field must name its entity");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("a skipped field must name the field");
        }
        note = note == null ? "" : note;
    }

    /** A one-line rendering for the survey, {@code Entity.field: note}. */
    public String describe() {
        return entity + "." + field + ": " + note;
    }
}
