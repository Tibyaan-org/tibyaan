package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;

import java.util.Map;
import java.util.Optional;

/**
 * Maps a Java type, as written in source, into the closed primitive type set (Volume 4
 * section 4).
 *
 * <p>The mapping is by simple type name, so both {@code BigDecimal} and
 * {@code java.math.BigDecimal} resolve. A type with no equivalent returns empty: the closed
 * set is deliberately small, and an unmapped type is a problem the caller surfaces, never a
 * silent passthrough (Volume 5 rule 3). The exact-inexact line is hard (Volume 5 section 4.6):
 * inexact Java floating point ({@code double}, {@code Double}, {@code float}, {@code Float}) maps
 * to {@code FLOAT}, exact {@code BigDecimal} maps to {@code DECIMAL}, and nothing crosses between
 * them in either direction.
 */
public final class JavaTypeMapper {

    private static final Map<String, Primitive> BY_SIMPLE_NAME = Map.ofEntries(
            Map.entry("String", Primitive.STRING),
            Map.entry("Long", Primitive.LONG),
            Map.entry("long", Primitive.LONG),
            Map.entry("Integer", Primitive.INTEGER),
            Map.entry("int", Primitive.INTEGER),
            Map.entry("Short", Primitive.INTEGER),
            Map.entry("short", Primitive.INTEGER),
            Map.entry("BigDecimal", Primitive.DECIMAL),
            Map.entry("double", Primitive.FLOAT),
            Map.entry("Double", Primitive.FLOAT),
            Map.entry("float", Primitive.FLOAT),
            Map.entry("Float", Primitive.FLOAT),
            Map.entry("Boolean", Primitive.BOOLEAN),
            Map.entry("boolean", Primitive.BOOLEAN),
            Map.entry("UUID", Primitive.UUID),
            Map.entry("Instant", Primitive.TIMESTAMP),
            Map.entry("LocalDate", Primitive.TIMESTAMP),
            Map.entry("LocalDateTime", Primitive.TIMESTAMP),
            Map.entry("OffsetDateTime", Primitive.TIMESTAMP),
            Map.entry("ZonedDateTime", Primitive.TIMESTAMP),
            Map.entry("Timestamp", Primitive.TIMESTAMP),
            Map.entry("Date", Primitive.TIMESTAMP));

    /** The mapped primitive type, or empty if the Java type has no equivalent. */
    public Optional<Type> map(String javaTypeAsWritten) {
        if (javaTypeAsWritten == null) {
            return Optional.empty();
        }
        String type = javaTypeAsWritten.trim();
        // Generic and array types (List<...>, Foo[]) have no place in the MVP type set.
        if (type.contains("<") || type.contains("[")) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_SIMPLE_NAME.get(simpleName(type)));
    }

    private static String simpleName(String type) {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }
}
