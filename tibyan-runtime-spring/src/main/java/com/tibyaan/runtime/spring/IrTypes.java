package com.tibyaan.runtime.spring;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps the IR's canonical type strings (Volume 4 section 4) to the JSON Schema type for the tool's input
 * schema, and coerces an incoming argument into the target Java type.
 *
 * <p>COERCION IS AGAINST THE METHOD'S DECLARED PARAMETER TYPE, never against a type reconstructed from the
 * IR (phase Enum Inputs, step EI-R; Volume 7 section 9). Reconstructing was the latent defect: a STRING
 * input yielded {@code String.class} while the real signature took a domain enum or a {@code LocalDate}, so
 * the method lookup failed and the capability was uninvokable. The adapter now reads the declared types
 * from the resolved method and converts into them.
 */
final class IrTypes {

    private static final Map<Class<?>, Class<?>> BOXED = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class);

    private IrTypes() {
    }

    static String jsonSchemaType(String irType) {
        return switch (baseType(irType)) {
            case "LONG", "INTEGER" -> "integer";
            case "DECIMAL" -> "number";
            case "BOOLEAN" -> "boolean";
            default -> "string";
        };
    }

    /**
     * Converts an incoming argument into the method's declared parameter type.
     *
     * @throws IllegalArgumentException when the value is not legal for the target type; for an enum the
     *                                  message NAMES THE LEGAL VALUES, which is the enforcement a schema
     *                                  closed set promises (Volume 7 section 9 item 4)
     */
    static Object coerce(Object value, Class<?> declaredType, String inputName) {
        Class<?> target = declaredType.isPrimitive() ? BOXED.get(declaredType) : declaredType;
        if (value == null) {
            if (declaredType.isPrimitive()) {
                throw new IllegalArgumentException("input '" + inputName + "' is required: the target "
                        + "parameter is the primitive type '" + declaredType.getName()
                        + "', which cannot accept a missing value");
            }
            return null;
        }
        if (target.isInstance(value)) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (target.isEnum()) {
            return EnumWireValues.match(target, text)
                    .orElseThrow(() -> new IllegalArgumentException("input '" + inputName + "' has value '"
                            + text + "', which is not one of the values '" + target.getSimpleName()
                            + "' accepts: " + String.join(", ", EnumWireValues.of(target))));
        }
        return convert(value, target, text, inputName);
    }

    private static Object convert(Object value, Class<?> target, String text, String inputName) {
        try {
            if (target == String.class) {
                return text;
            }
            if (target == Long.class) {
                return value instanceof Number n ? n.longValue() : Long.parseLong(text);
            }
            if (target == Integer.class) {
                return value instanceof Number n ? n.intValue() : Integer.parseInt(text);
            }
            if (target == Short.class) {
                return value instanceof Number n ? n.shortValue() : Short.parseShort(text);
            }
            if (target == Byte.class) {
                return value instanceof Number n ? n.byteValue() : Byte.parseByte(text);
            }
            if (target == Double.class) {
                return value instanceof Number n ? n.doubleValue() : Double.parseDouble(text);
            }
            if (target == Float.class) {
                return value instanceof Number n ? n.floatValue() : Float.parseFloat(text);
            }
            if (target == BigDecimal.class) {
                return new BigDecimal(text);
            }
            if (target == Boolean.class) {
                return value instanceof Boolean b ? b : Boolean.parseBoolean(text);
            }
            if (target == Character.class) {
                if (text.length() != 1) {
                    throw new IllegalArgumentException("expected a single character");
                }
                return text.charAt(0);
            }
            if (target == UUID.class) {
                return UUID.fromString(text);
            }
            if (target == Instant.class) {
                return Instant.parse(text);
            }
            if (target == LocalDate.class) {
                return LocalDate.parse(text);
            }
            if (target == LocalDateTime.class) {
                return LocalDateTime.parse(text);
            }
            if (target == LocalTime.class) {
                return LocalTime.parse(text);
            }
            if (target == OffsetDateTime.class) {
                return OffsetDateTime.parse(text);
            }
            if (target == ZonedDateTime.class) {
                return ZonedDateTime.parse(text);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("input '" + inputName + "' has value '" + text
                    + "', which is not a valid '" + target.getSimpleName() + "': " + e.getMessage(), e);
        }
        // An unconvertible target is a loud refusal, never a silent pass-through of a value the method
        // cannot accept: fail loud, never guess.
        throw new IllegalArgumentException("input '" + inputName + "' cannot be converted to the target "
                + "parameter type '" + target.getName() + "'");
    }

    /** The values an enum-typed parameter accepts, for diagnostics and tests. Empty for a non-enum. */
    static List<String> legalValues(Class<?> declaredType) {
        Class<?> target = declaredType.isPrimitive() ? BOXED.get(declaredType) : declaredType;
        return target != null && target.isEnum() ? EnumWireValues.of(target) : List.of();
    }

    private static String baseType(String irType) {
        int paren = irType.indexOf('(');
        return paren >= 0 ? irType.substring(0, paren) : irType;
    }
}
