package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds an OpenAPI 3.0 document from the Capability IR as an ordered value graph, then serializes
 * it deterministically (Volume 6 section 4). It reads only the IR: one GET operation per capability,
 * a component schema per entity in the reference closure of the entity-returning capabilities, and
 * the partiality-in-schema rule (section 4.1) applied to every schema it emits.
 */
final class OpenApiDocument {

    private static final String OPENAPI_VERSION = "3.0.3";
    private static final String VIEW_ANNOTATION = "view";
    private static final String PARTIAL_ANNOTATION = "partial";
    private static final String SCHEMAS_REF = "#/components/schemas/";

    private final CapabilityModel ir;
    private final Map<String, Entity> entitiesByName;

    private OpenApiDocument(CapabilityModel ir) {
        this.ir = ir;
        this.entitiesByName = new LinkedHashMap<>();
        for (Entity entity : ir.entities()) {
            entitiesByName.put(entity.name(), entity);
        }
    }

    static String toJson(CapabilityModel ir) {
        return Json.write(new OpenApiDocument(ir).build());
    }

    private Map<String, Object> build() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", OPENAPI_VERSION);
        document.put("info", info());
        document.put("paths", paths());
        Map<String, Object> schemas = componentSchemas();
        if (!schemas.isEmpty()) {
            Map<String, Object> components = new LinkedHashMap<>();
            components.put("schemas", schemas);
            document.put("components", components);
        }
        return document;
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", ir.module() + " API");
        info.put("version", ir.version());
        return info;
    }

    // One GET path per capability, in a deterministic order by path string.
    private Map<String, Object> paths() {
        List<Capability> ordered = new ArrayList<>(ir.capabilities());
        ordered.sort(Comparator.comparing(this::pathOf).thenComparing(Capability::name));
        Map<String, Object> paths = new LinkedHashMap<>();
        for (Capability capability : ordered) {
            paths.put(pathOf(capability), pathItem(capability));
        }
        return paths;
    }

    private String pathOf(Capability capability) {
        return "/" + lowerFirst(entityName(capability)) + "/{" + inputName(capability) + "}";
    }

    private Map<String, Object> pathItem(Capability capability) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", capability.name());
        operation.put("summary", description(capability));
        operation.put("parameters", List.of(pathParameter(capability)));
        operation.put("responses", responses(capability));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("get", operation);
        return item;
    }

    private Map<String, Object> pathParameter(Capability capability) {
        String name = inputName(capability);
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("name", name);
        parameter.put("in", "path");
        parameter.put("required", Boolean.TRUE);
        parameter.put("schema", typeSchema(capability.inputs().get(name)));
        return parameter;
    }

    private Map<String, Object> responses(Capability capability) {
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> mediaType = new LinkedHashMap<>();
        mediaType.put("schema", responseSchema(capability));
        content.put("application/json", mediaType);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "The " + entityName(capability) + " read by " + inputName(capability) + ".");
        ok.put("content", content);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", ok);
        return responses;
    }

    // Condition 2 and 3: entity-returning references the entity component; view-returning is a
    // standalone permissive object that declines to enumerate the view's fields.
    private Map<String, Object> responseSchema(Capability capability) {
        Optional<String> view = viewOf(capability);
        if (view.isPresent()) {
            return viewSchema(entityName(capability), view.get());
        }
        return ref(entityName(capability));
    }

    private static Map<String, Object> viewSchema(String entity, String view) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", "Returns " + view + ", a view of " + entity + "; the concrete field "
                + "shape of " + view + " is not modeled, so this schema does not enumerate its fields.");
        schema.put("additionalProperties", Boolean.TRUE);
        schema.put("x-tibyan-view", view);
        return schema;
    }

    // Component schemas for every entity in the reference closure of the entity-returning
    // capabilities, sorted by name. View capabilities reference no component (condition 3), so a
    // view-only IR yields no schemas.
    private Map<String, Object> componentSchemas() {
        TreeSet<String> referenced = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (Capability capability : ir.capabilities()) {
            if (viewOf(capability).isEmpty()) {
                queue.add(entityName(capability));
            }
        }
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (referenced.add(name)) {
                Entity entity = entitiesByName.get(name);
                if (entity != null) {
                    for (Field field : entity.fields()) {
                        if (field.type() instanceof EntityRef target) {
                            queue.add(target.entityName());
                        }
                    }
                }
            }
        }
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (String name : referenced) {
            Entity entity = entitiesByName.get(name);
            if (entity != null) {
                schemas.put(name, entitySchema(entity));
            }
        }
        return schemas;
    }

    // An entity component: the modeled fields as properties, non-nullable modeled fields required,
    // permissive (never closed, condition 1), with the partiality disclosure when the entity is
    // partial (condition 4). Skipped fields are absent from properties and required by construction.
    private Map<String, Object> entitySchema(Entity entity) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<Object> required = new ArrayList<>();
        for (Field field : entity.fields()) {
            properties.put(field.name(), fieldSchema(field));
            if (!field.nullable()) {
                required.add(field.name());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", entityDescription(entity));
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", Boolean.TRUE);
        List<String> partial = partialFields(entity);
        if (!partial.isEmpty()) {
            schema.put("x-tibyan-partial", new ArrayList<>(partial));
        }
        return schema;
    }

    private static String entityDescription(Entity entity) {
        String base = "Entity " + entity.name() + " as modeled by Tibyaan.";
        List<String> partial = partialFields(entity);
        if (partial.isEmpty()) {
            return base;
        }
        return base + " Partial model: " + partial.size() + " field(s) of the source entity are not "
                + "represented: " + String.join(", ", partial) + ".";
    }

    // A field schema. An enum field is a string with the recorded value set; an entity reference is
    // a bare $ref; a primitive maps per the table. A nullable non-reference field carries nullable.
    private Map<String, Object> fieldSchema(Field field) {
        if (field.type() instanceof EntityRef target) {
            return ref(target.entityName());
        }
        Map<String, Object> schema = typeSchema(field.type());
        enumValues(field).ifPresent(values -> schema.put("enum", new ArrayList<>(values)));
        if (field.nullable()) {
            schema.put("nullable", Boolean.TRUE);
        }
        return schema;
    }

    // The closed type set to OpenAPI (Volume 6 section 4 table). ENTITY_REF is handled by the caller
    // as a $ref; only primitives reach here.
    private Map<String, Object> typeSchema(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Primitive primitive = (Primitive) type;
        switch (primitive) {
            case STRING -> schema.put("type", "string");
            case LONG -> {
                schema.put("type", "integer");
                schema.put("format", "int64");
            }
            case INTEGER -> {
                schema.put("type", "integer");
                schema.put("format", "int32");
            }
            case DECIMAL -> {
                schema.put("type", "number");
                schema.put("format", "decimal");
            }
            case FLOAT -> {
                schema.put("type", "number");
                schema.put("format", "double");
            }
            case BOOLEAN -> schema.put("type", "boolean");
            case UUID -> {
                schema.put("type", "string");
                schema.put("format", "uuid");
            }
            case TIMESTAMP -> {
                schema.put("type", "string");
                schema.put("format", "date-time");
            }
            default -> throw new IllegalArgumentException("unmapped primitive: " + primitive);
        }
        return schema;
    }

    private static Map<String, Object> ref(String entityName) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("$ref", SCHEMAS_REF + entityName);
        return reference;
    }

    // The synthesized operation description, mirroring the MCP backend's (Volume 6 section 3.1).
    private String description(Capability capability) {
        List<String> inputs = new ArrayList<>(new TreeMap<>(capability.inputs()).keySet());
        String base = "Read " + entityName(capability)
                + (inputs.isEmpty() ? "." : " by " + String.join(", ", inputs) + ".");
        return viewOf(capability)
                .map(view -> base + " Returns the " + view + " view of " + entityName(capability) + ".")
                .orElse(base);
    }

    private String entityName(Capability capability) {
        if (capability.output() instanceof EntityRef ref) {
            return ref.entityName();
        }
        return capability.output().canonical();
    }

    // The single input name (read-by-id has exactly one input); deterministic for any input set.
    private static String inputName(Capability capability) {
        return new TreeMap<>(capability.inputs()).firstKey();
    }

    private static Optional<String> viewOf(Capability capability) {
        return firstValue(capability.annotations(), VIEW_ANNOTATION);
    }

    private static Optional<List<String>> enumValues(Field field) {
        for (Annotation annotation : field.annotations()) {
            if (annotation.key().equals("enum") && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values());
            }
        }
        return Optional.empty();
    }

    private static List<String> partialFields(Entity entity) {
        for (Annotation annotation : entity.annotations()) {
            if (annotation.key().equals(PARTIAL_ANNOTATION)) {
                return annotation.values();
            }
        }
        return List.of();
    }

    private static Optional<String> firstValue(List<Annotation> annotations, String key) {
        for (Annotation annotation : annotations) {
            if (annotation.key().equals(key) && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
    }

    private static String lowerFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
