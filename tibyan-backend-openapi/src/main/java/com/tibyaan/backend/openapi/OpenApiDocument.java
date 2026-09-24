package com.tibyaan.backend.openapi;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
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

    // Phase DL-1 (Volume 6 section 4.6): the reserved prefix of a reviewed capability (Volume 4 rule 13),
    // the tag every reviewed operation carries, and the three annotations the provider's draft intake
    // attaches: the reviewer's sentence verbatim, the bare void flag, and the opaque return type as written.
    private static final String REVIEWED_PREFIX = "reviewed_";
    private static final String REVIEWED_TAG = "reviewed";
    private static final String DESCRIPTION_ANNOTATION = "description";
    private static final String VOID_ANNOTATION = "void";
    private static final String OPAQUE_OUTPUT_ANNOTATION = "opaqueOutput";

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
        // A reviewed capability's path follows the attested convention (Volume 6 section 4.4 condition 0),
        // derived from its minted name and never from an entity: its output may be a primitive, an opaque
        // type, or nothing, so no entity path exists to derive, and name uniqueness (Volume 4 rule 2)
        // makes the path unique. No path parameter: nothing proves any reviewed input is an identifier
        // (the Read-Identity lesson, Volume 5 section 4.1.1), so no input becomes a path segment.
        if (capability.hasReview()) {
            return "/reviewed/" + capability.name().substring(REVIEWED_PREFIX.length());
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
        if (capability.hasReview()) {
            return reviewedPathItem(capability);
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
        // AT-3: a boundary operation leads with the stronger label and names the security owner.
        operation.put("summary", attestation.hasBoundaryClass()
                ? "Human-attested BOUNDARY, not tool-verified: its read-only-ness or its security contract "
                        + "rests on a human assertion, not on structure. " + attestation.resultSemantic()
                        + " Asserted by " + attestation.assertedBy() + " ("
                        + attestation.origin().token() + "), " + attestation.date() + ", security owner "
                        + attestation.securityOwner() + "."
                : "Human-attested, not tool-verified. " + attestation.resultSemantic()
                        + " Asserted by " + attestation.assertedBy() + " ("
                        + attestation.origin().token() + "), " + attestation.date() + ".");
        List<Object> parameters = attestedParameters(capability);
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        operation.put("responses", responses(capability));
        operation.put("x-tibyan-attested", Boolean.TRUE);
        if (attestation.hasBoundaryClass()) {
            operation.put("x-tibyan-attested-boundary", Boolean.TRUE);
        }
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
            // Phase Enum Inputs (Volume 6 section 4.5): the constraint renders as the parameter's
            // schema.enum, in declaration order, with no vendor extension because `enum` is native to the
            // OpenAPI schema object and means exactly what this phase means by it. Same honesty gate as
            // MCP: the key is emitted only because the runtime enforces it.
            Map<String, Object> schema = typeSchema(input.getValue());
            List<String> values = capability.inputValues().get(input.getKey());
            if (values != null && !values.isEmpty()) {
                schema = new LinkedHashMap<>(schema);
                schema.put("enum", List.copyOf(values));
            }
            schema = arrayIfMultiple(capability, input.getKey(), schema, parameter);
            parameter.put("schema", schema);
            parameter.put("description", attestedInputDescription(capability, input.getKey()));
            parameters.add(parameter);
        }
        return parameters;
    }

    private static Map<String, Object> attestationObject(Attestation attestation) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("category", attestation.category().token());
        object.put("resultSemantic", attestation.resultSemantic());
        // The asserted subject entity, present only on a shape claim (AT-2): it IS the claim there, so it
        // travels with the rest of the words. A predicate attestation carries none and renders unchanged.
        if (attestation.subjectEntity() != null) {
            object.put("subjectEntity", attestation.subjectEntity());
        }
        object.put("assertedBy", attestation.assertedBy());
        object.put("origin", attestation.origin().token());
        object.put("date", attestation.date());
        // AT-3: the security fields, boundary only. assertedAuthorities never becomes a compiled
        // security requirement and never clears the securityUnverifiable marker.
        if (attestation.hasBoundaryClass()) {
            object.put("securityOwner", attestation.securityOwner());
            object.put("readOnlyAffirmed", attestation.readOnlyAffirmed());
            if (!attestation.assertedAuthorities().isEmpty()) {
                object.put("assertedAuthorities", List.copyOf(attestation.assertedAuthorities()));
            }
        }
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

    // A reviewed operation (Volume 6 section 4.6; Volume 5 section 4.17.5). A reviewed READ is a GET whose
    // inputs are query parameters; a WRITE or ACTION is a POST whose request body carries the inputs as
    // an object. Both are tagged `reviewed`, lead their summary with the class sentence, and carry the
    // review object as a vendor extension (the machine half, the two-audiences discipline of section 4.1
    // condition 6). The response claims only what the IR carries: a primitive maps, an entity is its
    // component, a void method is 204 with no body, and an opaque output is the permissive object of
    // section 4.1, exactly the posture a view already has. A document with no reviewed capability is
    // byte-identical (fixture RV-12).
    private Map<String, Object> reviewedPathItem(Capability capability) {
        Review review = capability.review();
        boolean mutating = capability.kind() != CapabilityKind.READ;
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", capability.name());
        operation.put("tags", List.of(REVIEWED_TAG));
        operation.put("summary", reviewedSummary(capability) + securityClause(capability));
        if (mutating) {
            // A write's inputs travel in the body, never in the URL. A write with no inputs has no body.
            if (!capability.inputs().isEmpty()) {
                operation.put("requestBody", reviewedRequestBody(capability));
            }
        } else {
            List<Object> parameters = reviewedParameters(capability);
            if (!parameters.isEmpty()) {
                operation.put("parameters", parameters);
            }
        }
        operation.put("responses", reviewedResponses(capability));
        // The declared security contract, composed for reviewed capabilities exactly as for the others
        // (Volume 5 section 4.17.6): a reviewed write fronted by a secured controller is the first-order
        // risk, so its contract is on the surface in both halves, the extension here and the clause above.
        requiredAuthoritiesOf(capability).ifPresent(authorities ->
                operation.put("x-tibyan-required-authorities", new ArrayList<>(authorities)));
        if (securityUnverifiableReason(capability).isPresent()) {
            operation.put("x-tibyan-security-unverifiable", Boolean.TRUE);
        }
        operation.put("x-tibyan-reviewed", Boolean.TRUE);
        operation.put("x-tibyan-review", reviewObject(review));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put(mutating ? "post" : "get", operation);
        return item;
    }

    // The class sentence first, so truncation never strips it, then the reviewer's words verbatim. When
    // the provider attached no description the class sentence stands alone: the tool never authors one.
    private static String reviewedSummary(Capability capability) {
        Review review = capability.review();
        String lead = "Reviewed by " + review.reviewedBy() + " (" + review.descriptionOrigin()
                + "), not tool-verified; the tool labeled it " + review.label().name() + ".";
        return firstValue(capability.annotations(), DESCRIPTION_ANNOTATION)
                .filter(words -> !words.isBlank())
                .map(words -> lead + " " + words)
                .orElse(lead);
    }

    // Every input is a required query parameter: a method parameter is bound on every invocation, and no
    // review asserts optionality. A value constraint renders as the parameter's schema.enum exactly as it
    // does today (section 4.5), under the same honesty gate.
    private List<Object> reviewedParameters(Capability capability) {
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<String, Type> input : capability.inputs().entrySet()) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", input.getKey());
            parameter.put("in", "query");
            parameter.put("required", Boolean.TRUE);
            parameter.put("schema", arrayIfMultiple(capability, input.getKey(),
                    inputSchema(capability, input.getKey(), input.getValue()), parameter));
            parameters.add(parameter);
        }
        return parameters;
    }

    // The inputs as one JSON object: every input a property, every input required, each typed per the
    // mapping table with its value constraint where one exists. The object is not marked closed and not
    // marked open: the IR carries the inputs and claims nothing about anything else.
    private Map<String, Object> reviewedRequestBody(Capability capability) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<Object> required = new ArrayList<>();
        for (Map.Entry<String, Type> input : capability.inputs().entrySet()) {
            properties.put(input.getKey(), arrayIfMultiple(capability, input.getKey(),
                    inputSchema(capability, input.getKey(), input.getValue()), null));
            required.add(input.getKey());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);

        Map<String, Object> mediaType = new LinkedHashMap<>();
        mediaType.put("schema", schema);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", mediaType);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("required", Boolean.TRUE);
        body.put("content", content);
        return body;
    }

    // Phase DL-2a (Volume 6 section 4.7): a multiple input renders as an array of its element schema, the
    // closed set staying on the items; a query parameter carrying it is form-style and exploded, the
    // OpenAPI default spelled out. An input that is not multiple is returned untouched.
    private static Map<String, Object> arrayIfMultiple(Capability capability, String name,
                                                       Map<String, Object> element, Map<String, Object> parameter) {
        if (!capability.multipleInputs().contains(name)) {
            return element;
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", element);
        if (parameter != null) {
            parameter.put("style", "form");
            parameter.put("explode", Boolean.TRUE);
        }
        return array;
    }

    private Map<String, Object> inputSchema(Capability capability, String name, Type type) {
        Map<String, Object> schema = typeSchema(type);
        List<String> values = capability.inputValues().get(name);
        if (values != null && !values.isEmpty()) {
            schema.put("enum", List.copyOf(values));
        }
        return schema;
    }

    // A void method responds 204 with no body; anything else responds 200 with the honest schema.
    private Map<String, Object> reviewedResponses(Capability capability) {
        Map<String, Object> responses = new LinkedHashMap<>();
        String kindWord = reviewedKindWord(capability);
        if (hasAnnotation(capability, VOID_ANNOTATION)) {
            Map<String, Object> noContent = new LinkedHashMap<>();
            noContent.put("description", "Performed; the reviewed " + kindWord + " returns nothing.");
            responses.put("204", noContent);
            return responses;
        }
        Map<String, Object> mediaType = new LinkedHashMap<>();
        mediaType.put("schema", reviewedResponseSchema(capability));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("application/json", mediaType);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "The result of the reviewed " + kindWord + ", as the reviewer described it.");
        ok.put("content", content);
        responses.put("200", ok);
        return responses;
    }

    // RV-F, in schema form: an opaque output is the permissive object (condition 3's posture, with the
    // written type named where a view names its type); an entity is its component; a primitive maps. A
    // collection output wraps the element in an array, as a list read does (section 4.2).
    private Map<String, Object> reviewedResponseSchema(Capability capability) {
        Optional<String> opaque = firstValue(capability.annotations(), OPAQUE_OUTPUT_ANNOTATION);
        Map<String, Object> element;
        if (opaque.isPresent()) {
            element = opaqueSchema(opaque.get());
        } else if (capability.output() instanceof EntityRef ref) {
            element = ref(ref.entityName());
        } else {
            element = typeSchema(capability.output());
        }
        if (!capability.outputMultiple()) {
            return element;
        }
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", element);
        return array;
    }

    private static Map<String, Object> opaqueSchema(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", "Returns " + type + "; the concrete field shape of " + type
                + " is not modeled, so this schema does not enumerate its fields.");
        schema.put("additionalProperties", Boolean.TRUE);
        schema.put("x-tibyan-opaque-output", type);
        return schema;
    }

    private static String reviewedKindWord(Capability capability) {
        return switch (capability.kind()) {
            case READ -> "read";
            case WRITE -> "write";
            case ACTION -> "action";
        };
    }

    // The review object, character-exact from the component (Volume 6 section 3.9's object, mirrored):
    // the tool's label beside the human's name, date and the origin of the words. The fingerprint is the
    // intake's pin and stays off the surface, as the design lists the object without it.
    private static Map<String, Object> reviewObject(Review review) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("label", review.label().name());
        object.put("reviewedBy", review.reviewedBy());
        object.put("reviewedOn", review.reviewedOn());
        object.put("descriptionOrigin", review.descriptionOrigin());
        return object;
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
            // Only an entity output seeds the closure. A reviewed capability's output may be a primitive
            // or a placeholder behind a void or opaqueOutput annotation, none of which names a component;
            // every verified and attested output is an entity reference, so their closure is unchanged.
            if (viewOf(capability).isEmpty() && capability.output() instanceof EntityRef ref
                    && !hasAnnotation(capability, VOID_ANNOTATION)
                    && !hasAnnotation(capability, OPAQUE_OUTPUT_ANNOTATION)) {
                queue.add(ref.entityName());
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
