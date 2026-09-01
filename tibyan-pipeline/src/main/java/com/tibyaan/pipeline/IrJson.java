package com.tibyaan.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the Capability IR as inspectable JSON. This is the productionized form of the
 * serializer P0 kept in test scope: {@link Type} and {@link BindingRef} serialize to their
 * canonical string forms (for example {@code "ENTITY_REF(Product)"}), and every other IR type is
 * a plain record Jackson maps through its canonical constructor.
 *
 * <p>The IR JSON is the intermediate artifact between {@code tibyan scan} and
 * {@code tibyan generate}. It is not the runtime manifest: the manifest is the backend's output.
 */
public final class IrJson {

    private static final ObjectMapper MAPPER = buildMapper();

    private IrJson() {
    }

    /** Writes the model as pretty printed JSON to the given file, creating parent directories. */
    public static void write(CapabilityModel model, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, writeString(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write IR to " + file, e);
        }
    }

    /** Renders the model as pretty printed JSON. */
    public static String writeString(CapabilityModel model) {
        try {
            return MAPPER.writeValueAsString(model);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to serialize IR", e);
        }
    }

    /** Reads a model from the given JSON file. */
    public static CapabilityModel read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, CapabilityModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read IR from " + file, e);
        }
    }

    private static ObjectMapper buildMapper() {
        SimpleModule module = new SimpleModule("tibyan-ir");
        module.addSerializer(Type.class, new TypeSerializer());
        module.addDeserializer(Type.class, new TypeDeserializer());
        module.addSerializer(BindingRef.class, new BindingRefSerializer());
        module.addDeserializer(BindingRef.class, new BindingRefDeserializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // The optional Attestation component (Volume 4 section 3.8) is omitted when absent, so an
        // attestation-free model serializes byte-identically to before this phase (fixture ATT-11). The
        // override is scoped to the two attestation-bearing types, so no other record's serialization
        // changes; Capability's other fields are never null, so only `attestation` is affected, and on
        // Attestation only the optional `subjectEntity` is.
        mapper.configOverride(Capability.class)
                .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null));
        mapper.configOverride(Attestation.class)
                .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null));
        // The optional inputValues component (Volume 4 section 3.9, phase Enum Inputs) is omitted when
        // EMPTY, so a constraint-free model serializes byte-identically. The override is scoped to that
        // one property through a mixin rather than applied to Capability as a whole, because a type-wide
        // NON_EMPTY would also drop the empty `annotations` list that every unannotated capability
        // currently serializes, which would change existing output.
        mapper.addMixIn(Capability.class, CapabilityInputValuesMixin.class);
        return mapper;
    }

    /** Scopes NON_EMPTY to Capability's inputValues alone (Volume 4 section 3.9). */
    private abstract static class CapabilityInputValuesMixin {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        abstract Map<String, List<String>> inputValues();
    }

    private static final class TypeSerializer extends JsonSerializer<Type> {
        @Override
        public void serialize(Type value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.canonical());
        }
    }

    private static final class TypeDeserializer extends JsonDeserializer<Type> {
        @Override
        public Type deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Type.fromCanonical(p.getValueAsString());
        }
    }

    private static final class BindingRefSerializer extends JsonSerializer<BindingRef> {
        @Override
        public void serialize(BindingRef value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.value());
        }
    }

    private static final class BindingRefDeserializer extends JsonDeserializer<BindingRef> {
        @Override
        public BindingRef deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new BindingRef(p.getValueAsString());
        }
    }
}
