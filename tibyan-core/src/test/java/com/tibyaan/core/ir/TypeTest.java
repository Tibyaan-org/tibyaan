package com.tibyaan.core.ir;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeTest {

    @Test
    void primitiveCanonicalIsItsName() {
        assertThat(Primitive.LONG.canonical()).isEqualTo("LONG");
        assertThat(Primitive.DECIMAL.canonical()).isEqualTo("DECIMAL");
    }

    @Test
    void entityRefCanonicalWrapsTheName() {
        assertThat(Type.entityRef("Product").canonical()).isEqualTo("ENTITY_REF(Product)");
    }

    @Test
    void everyPrimitiveRoundTripsThroughItsCanonicalForm() {
        for (Primitive primitive : Primitive.values()) {
            assertThat(Type.fromCanonical(primitive.canonical())).isEqualTo(primitive);
        }
    }

    @Test
    void entityRefRoundTripsThroughItsCanonicalForm() {
        Type ref = Type.entityRef("Order");
        assertThat(Type.fromCanonical(ref.canonical())).isEqualTo(ref);
    }

    @Test
    void unknownTypeStringIsRejected() {
        assertThatThrownBy(() -> Type.fromCanonical("NOT_A_TYPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown type");
    }

    @Test
    void blankEntityRefNameIsRejected() {
        assertThatThrownBy(() -> Type.entityRef("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
