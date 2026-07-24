package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Attestation;
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
    private static final String PAGED_ANNOTATION = "paged";
    private static final String REQUIRED_AUTHORITIES_ANNOTATION = "requiredAuthorities";
    private static final String SECURITY_UNVERIFIABLE_ANNOTATION = "securityUnverifiable";
    private static final String SCHEMAS_REF = "#/components/schemas/";
    private static final String ATTESTED_PREFIX = "attested_";

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
            String path = pathOf(capability);
            // The backend independently checks that no two emitted operations share a path (Volume 6
            // section 4.4 condition 0): a collision can never silently overwrite one operation with another.
            if (paths.containsKey(path)) {
                throw new IllegalStateException("two capabilities emit the same OpenAPI path '" + path
                        + "'; a path collision would silently drop an operation (Volume 6 section 4.4)");
            }
            paths.put(path, pathItem(capability));
        }
        return paths;
    }

    // A list read has no id input, so its path is the collection path with no path parameter
    // (Volume 6 section 4.2); a by-id read keeps the item path with the single id parameter. An attested
    // read's path is derived from its minted name, never the subject entity (Volume 6 section 4.4
    // condition 0), so two attested reads of one entity never collide on a path.
    private String pathOf(Capability capability) {
        if (capability.hasAttestation()) {
            String base = "/attested/" + capability.name().substring(ATTESTED_PREFIX.length());
            return idInputName(capability).map(id -> base + "/{" + id + "}").orElse(base);
        }
        if (capability.outputMultiple()) {
            return "/" + lowerFirst(entityName(capability));
        }
        return "/" + lowerFirst(entityName(capability)) + "/{" + inputName(capability) + "}";
    }

    private Map<String, Object> pathItem(Capability capability) {
        if (capability.hasAttestation()) {
            return attestedPathItem(capability);
        }
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", capability.name());
        operation.put("summary", description(capability) + securityClause(capability));
        if (!capability.outputMultiple()) {
            operation.put("parameters", List.of(pathParameter(capability)));
        }
        operation.put("responses", responses(capability));
        // The declared security contract as a vendor extension (Volume 6 section 4.3): never the native
        // securitySchemes vocabulary, which would fabricate a credential type/flow/scope the IR does not
        // carry. Additive and present only when a contract is declared, so absent operations are identical.
        requiredAuthoritiesOf(capability).ifPresent(authorities ->
                operation.put("x-tibyan-required-authorities", new ArrayList<>(authorities)));
        if (securityUnverifiableReason(capability).isPresent()) {
            operation.put("x-tibyan-security-unverifiable", Boolean.TRUE);
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("get", operation);
        return item;
    }

    // The security clause the operation summary carries (Volume 6 section 4.3), the human half of the
    // two-audience disclosure. A compiled contract states its any-of authorities and that the credential
    // shape is not modeled; an unverifiable one warns and names the reason; an absent contract adds nothing.
    private String securityClause(Capability capability) {
        Optional<List<String>> authorities = requiredAuthoritiesOf(capability);
        if (authorities.isPresent()) {
            return " Requires any of the declared authorities: " + String.join(", ", authorities.get())
                    + ". The application's security layer matches these literally; how a caller acquires "
                    + "and presents them is not modeled.";
        }
        return securityUnverifiableReason(capability)
                .map(reason -> " A declared security contract exists on this operation that could not be "
                        + "structurally verified (" + reason + "); resolve it before relying on this operation.")
                .orElse("");
    }

    private static Optional<List<String>> requiredAuthoritiesOf(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(REQUIRED_AUTHORITIES_ANNOTATION)) {
                return Optional.of(annotation.values());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> securityUnverifiableReason(Capability capability) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(SECURITY_UNVERIFIABLE_ANNOTATION) && !annotation.values().isEmpty()) {
                return Optional.of(annotation.values().get(0));
            }
        }
        return Optional.empty();
    }

    // An attested operation (Volume 6 section 4.4): the class is in the operationId and the summary; the
    // machine half is x-tibyan-attested and x-tibyan-attestation; every input is a query parameter (a
    // filter is not a path segment) except a structurally-proven id, which stays a path parameter; the
    // response claims only what is structurally tied. Nothing else changes; a document with no attested
    // capability is byte-identical.
    private Map<String, Object> attestedPathItem(Capability capability) {
        Attestation attestation = capability.attestation();
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", capability.name());
        operation.put("summary", "Human-attested, not tool-verified. " + attestation.resultSemantic()
                + " Asserted by " + attestation.assertedBy() + " (" + attestation.origin().token() + "), "
                + attestation.date() + ".");
        List<Object> parameters = attestedParameters(capability);
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        operation.put("responses", responses(capability));
        operation.put("x-tibyan-attested", Boolean.TRUE);
        operation.put("x-tibyan-attestation", attestationObject(attestation));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("get", operation);
        return item;
    }

    private List<Object> attestedParameters(Capability capability) {
        Optional<String> idInput = idInputName(capability);
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<String, Type> input : capability.inputs().entrySet()) {
            boolean isId = idInput.filter(input.getKey()::equals).isPresent();
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", input.getKey());
            parameter.put("in", isId ? "path" : "query");
            parameter.put("required", isId ? Boolean.TRUE : Boolean.FALSE);
            parameter.put("schema", typeSchema(input.getValue()));
            parameter.put("description", attestedInputDescription(capability, input.getKey()));
            parameters.add(parameter);
        }
        return parameters;
    }

    private static Map<String, Object> attestationObject(Attestation attestation) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("category", attestation.category().token());
        object.put("resultSemantic", attestation.resultSemantic());
        object.put("assertedBy", attestation.assertedBy());
        object.put("origin", attestation.origin().token());
        object.put("date", attestation.date());
        object.put("fingerprint", attestation.fingerprint());
        return object;
    }

    private String attestedInputDescription(Capability capability, String input) {
        String coverage = capability.attestation().parameterMeanings().get(input);
        if (Attestation.PAGING_MARK.equals(coverage)) {
            return "Paging or ordering parameter.";
        }
        if (Attestation.ID_MARK.equals(coverage)) {
            return "The " + entityName(capability) + " identifier.";
        }
        return coverage;
    }

    // The input covered by the reserved id mark (a structurally-proven identifier), if any.
    private static Optional<String> idInputName(Capability capability) {
        if (!capability.hasAttestation()) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> meaning : capability.attestation().parameterMeanings().entrySet()) {
            if (Attestation.ID_MARK.equals(meaning.getValue())) {
                return Optional.of(meaning.getKey());
            }
        }
        return Optional.empty();
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
        ok.put("description", okDescription(capability));
        ok.put("content", content);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", ok);
        return responses;
    }

    private String okDescription(Capability capability) {
        if (capability.hasAttestation()) {
            return capability.outputMultiple()
                    ? "An attested list of " + entityName(capability) + "."
                    : "An attested read of " + entityName(capability) + ".";
        }
        if (capability.outputMultiple()) {
            return "A list of " + entityName(capability) + ".";
        }
        return "The " + entityName(capability) + " read by " + inputName(capability) + ".";
    }

    // Condition 2 and 3: entity-returning references the entity component; view-returning is a
    // standalone permissive object that declines to enumerate the view's fields. A list read (Volume 6
    // section 4.2) is an array whose items are the element schema, with the paged envelope disclosed.
    private Map<String, Object> responseSchema(Capability capability) {
        if (capability.outputMultiple()) {
            return listSchema(capability);
        }
        Optional<String> view = viewOf(capability);
        if (view.isPresent()) {
            return viewSchema(entityName(capability), view.get());
        }
        return ref(entityName(capability));
    }

    // An array of the element schema. E4 list reads are entity-returning, so the items are a $ref to the
    // element entity's component (with its own partiality disclosure, section 4.1). A paged read carries
    // x-tibyan-paged and a prose disclosure: the wire is a page envelope, but only the element collection
    // is modeled, never a fabricated page object (Volume 6 section 4.2).
    private Map<String, Object> listSchema(Capability capability) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", ref(entityName(capability)));
        if (hasAnnotation(capability, PAGED_ANNOTATION)) {
            schema.put("description", "The wire response is a page envelope; this schema models the "
                    + "element collection, not the page envelope.");
            schema.put("x-tibyan-paged", Boolean.TRUE);
        }
        return schema;
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

    // The synthesized operation description, mirroring the MCP backend's (Volume 6 sections 3.1, 3.3).
    private String description(Capability capability) {
        if (capability.outputMultiple()) {
            String base = "List all " + entityName(capability) + ".";
            return hasAnnotation(capability, PAGED_ANNOTATION)
                    ? base + " Returns a page of results; the total count and page envelope are not modeled."
                    : base;
        }
        List<String> inputs = new ArrayList<>(new TreeMap<>(capability.inputs()).keySet());
        String base = "Read " + entityName(capability)
                + (inputs.isEmpty() ? "." : " by " + String.join(", ", inputs) + ".");
        return viewOf(capability)
                .map(view -> base + " Returns the " + view + " view of " + entityName(capability) + ".")
                .orElse(base);
    }

    private static boolean hasAnnotation(Capability capability, String key) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(key)) {
                return true;
            }
        }
        return false;
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
