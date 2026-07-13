package com.tibyaan.pipeline;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.ValidationException;
import com.tibyaan.plugin.Backend;
import com.tibyaan.plugin.CapabilityProvider;
import com.tibyaan.plugin.GenerationContext;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pipeline orchestrates through the plugin interfaces and gates on validation. These tests use
 * in-test fakes for the provider and backend, so the pipeline is proven without depending on any
 * concrete plugin.
 */
class TibyanCompilerTest {

    private final TibyanCompiler compiler = new TibyanCompiler();
    private final ProviderInput anyInput = new ProviderInput(Path.of("."));

    @Test
    void scanReturnsTheValidatedIr() {
        CapabilityModel ir = compiler.scan(provider(validModel()), anyInput);
        assertThat(ir.entities()).extracting(Entity::name).containsExactly("Product");
        assertThat(ir.capabilities()).extracting(Capability::name).containsExactly("getProduct");
    }

    @Test
    void scanRefusesAnInvalidIr() {
        assertThatThrownBy(() -> compiler.scan(provider(invalidModel()), anyInput))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void generateRunsTheBackendForValidIr() {
        RecordingBackend backend = new RecordingBackend();
        GenerationContext context = new GenerationContext(Path.of("out"));

        compiler.generate(backend, validModel(), context);

        assertThat(backend.generated).isTrue();
    }

    @Test
    void generateRefusesAnInvalidIrAndNeverCallsTheBackend() {
        RecordingBackend backend = new RecordingBackend();

        assertThatThrownBy(() -> compiler.generate(backend, invalidModel(), new GenerationContext(Path.of("out"))))
                .isInstanceOf(ValidationException.class);

        assertThat(backend.generated).as("the backend must not run on invalid IR").isFalse();
    }

    @Test
    void validateReportsErrorsWithoutThrowing() {
        assertThat(compiler.validate(validModel()).isValid()).isTrue();
        assertThat(compiler.validate(invalidModel()).isValid()).isFalse();
    }

    private static CapabilityProvider provider(CapabilityModel model) {
        return input -> model;
    }

    private static CapabilityModel validModel() {
        Entity product = new Entity("Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);
        Capability getProduct = Capability.read("getProduct", inputs, Type.entityRef("Product"),
                new BindingRef("ProductService#findById"));
        return new CapabilityModel("legacy-spring-app", "0.1",
                List.of(product), List.of(getProduct), List.of(), Metadata.empty());
    }

    // A capability whose output references an entity that does not exist: unresolved reference.
    private static CapabilityModel invalidModel() {
        Capability getGhost = Capability.read("getGhost", Map.of(), Type.entityRef("Ghost"),
                new BindingRef("GhostService#findById"));
        return new CapabilityModel("m", "0.1",
                List.of(), List.of(getGhost), List.of(), Metadata.empty());
    }

    private static final class RecordingBackend implements Backend {
        boolean generated;

        @Override
        public void generate(CapabilityModel ir, GenerationContext ctx) {
            generated = true;
        }
    }
}
