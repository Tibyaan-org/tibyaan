package com.tibyaan.runtime.spring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps the IR's canonical type strings (Volume 4 section 4) to what the runtime needs: the Java
 * class for reflective method resolution, the JSON Schema type for the tool's input schema, and
 * coercion of an incoming argument into the target Java type.
 *
 * <p>This is the runtime side of the closed type system. Entity reference types are not used as
 * inputs in the MVP; only the primitive set is resolved here.
 */
final class IrTypes {

    private IrTypes() {
    }

    static Class<?> toJavaClass(String irType) {
        return switch (baseType(irType)) {
            case "LONG" -> Long.class;
            case "INTEGER" -> Integer.class;
            case "STRING" -> String.class;
            case "DECIMAL" -> BigDecimal.class;
            case "BOOLEAN" -> Boolean.class;
            case "UUID" -> UUID.class;
            case "TIMESTAMP" -> Instant.class;
            default -> Object.class;
        };
    }

    static String jsonSchemaType(String irType) {
        return switch (baseType(irType)) {
            case "LONG", "INTEGER" -> "integer";
            case "DECIMAL" -> "number";
            case "BOOLEAN" -> "boolean";
            default -> "string";
        };
    }

    static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (target == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(text);
        }
        if (target == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(text);
        }
        if (target == BigDecimal.class) {
            return new BigDecimal(text);
        }
        if (target == Boolean.class) {
            return value instanceof Boolean b ? b : Boolean.parseBoolean(text);
        }
        if (target == UUID.class) {
            return UUID.fromString(text);
        }
        if (target == String.class) {
            return text;
        }
        return value;
    }

    private static String baseType(String irType) {
        int paren = irType.indexOf('(');
        return paren >= 0 ? irType.substring(0, paren) : irType;
    }
}
