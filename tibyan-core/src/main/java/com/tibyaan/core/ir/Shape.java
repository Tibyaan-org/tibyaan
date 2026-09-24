package com.tibyaan.core.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The wire form of an in-tree class the runtime constructs from a JSON object: a DTO used as a capability
 * input (Volume 4 section 3.12, phase DL-2b). A Shape is NOT an {@link Entity}: it has no identity in the
 * system, is never persisted, is never the target of a relation and never the subject of a read.
 *
 * @param key            the class's canonical qualified name, unique within the model
 * @param name           the display name the {@code SHAPE_REF(name)} token refers to, unique within the model
 * @param fields         the mapped fields in declaration order, each typed by a primitive or a {@link ShapeRef}
 * @param multipleFields the names of fields whose wire form is an array of the field's type
 * @param fieldValues    per-field closed value sets, the section 3.9 contract at field level
 * @param annotations    carries {@code partial} naming the skipped fields with their declared types
 */
public record Shape(
        String key,
        String name,
        List<Field> fields,
        Set<String> multipleFields,
        Map<String, List<String>> fieldValues,
        List<Annotation> annotations) {

    public static final String PARTIAL_ANNOTATION = "partial";

    public Shape {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("shape key must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("shape '" + key + "' must have a name");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        multipleFields = multipleFields == null || multipleFields.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(multipleFields));
        if (fieldValues == null || fieldValues.isEmpty()) {
            fieldValues = Map.of();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : fieldValues.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            fieldValues = Collections.unmodifiableMap(copy);
        }
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }

    /** The skipped fields this shape discloses, empty when the shape is complete. */
    public List<String> partialFields() {
        for (Annotation annotation : annotations) {
            if (PARTIAL_ANNOTATION.equals(annotation.key())) {
                return annotation.values();
            }
        }
        return List.of();
    }
}
