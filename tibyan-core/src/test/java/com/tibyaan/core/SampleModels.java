package com.tibyaan.core;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand built models used across the core tests. The canonical one is the Volume 4 section 6
 * example: the {@code inventory} module with one {@code Product} entity and one read only
 * {@code getProduct} capability.
 */
public final class SampleModels {

    private SampleModels() {
    }

    /** The Volume 4 section 6 example, the MVP target IR. */
    public static CapabilityModel inventory() {
        Entity product = new Entity(
                "Product",
                List.of(
                        Field.of("id", Primitive.LONG, false),
                        Field.of("name", Primitive.STRING, false),
                        Field.of("price", Primitive.DECIMAL, false)),
                List.of(),
                Metadata.empty());

        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put("id", Primitive.LONG);

        Capability getProduct = Capability.read(
                "getProduct",
                inputs,
                Type.entityRef("Product"),
                new BindingRef("inventory.ProductService#findById"));

        return new CapabilityModel(
                "inventory",
                "0.1",
                List.of(product),
                List.of(getProduct),
                List.of(),
                Metadata.empty());
    }
}
