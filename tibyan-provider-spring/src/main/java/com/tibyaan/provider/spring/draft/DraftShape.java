package com.tibyaan.provider.spring.draft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A structured input as the reviewer sees it in the drafts file (Volume 5 section 4.19.3, phase DL-2b): the
 * class, every field the tool maps with its type, and every field it skips with the reason, so the file a
 * reviewer approves shows the whole object.
 *
 * @param key     the class's qualified name
 * @param fields  the mapped fields in declaration order
 * @param skipped the fields the tool does not model, each as {@code name: type (reason)}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DraftShape(String key, List<DraftShapeField> fields, List<String> skipped) {

    /** One mapped field: a primitive, an enum with its values, a collection of either, or a nested shape. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftShapeField(String name, String javaType, String type,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Boolean multiple,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) List<String> values,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) DraftShape shape) {
    }
}
