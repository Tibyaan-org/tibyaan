package com.tibyaan.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Type;

import java.io.IOException;

/**
 * Test only JSON support for the IR.
 *
 * <p>This lives in test scope on purpose. {@code tibyan-core} ships with zero dependencies, so
 * Jackson never reaches the core artifact (CLAUDE.md hard rule 1). The only types that need
 * custom handling are {@link Type} and {@link BindingRef}, which serialize to their canonical
 * string forms (for example {@code "ENTITY_REF(Product)"}); every other IR type is a plain
 * record that Jackson maps through its canonical constructor.
 */
final class IrTestJson {

    private IrTestJson() {
    }

    static ObjectMapper mapper() {
        SimpleModule module = new SimpleModule("tibyan-ir-test");
        module.addSerializer(Type.class, new TypeSerializer());
        module.addDeserializer(Type.class, new TypeDeserializer());
        module.addSerializer(BindingRef.class, new BindingRefSerializer());
        module.addDeserializer(BindingRef.class, new BindingRefDeserializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
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
