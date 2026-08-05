package com.tibyaan.runtime.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The wire form of a Java enum, read reflectively at invocation time (phase Enum Inputs, step EI-R;
 * Volume 5 section 4.16.4, Volume 7 section 9 item 3).
 *
 * <p>The values an application accepts for an enum-typed parameter are NOT always the constant names. This
 * class implements exactly the rule the provider uses to decide what to advertise, so that what is
 * advertised and what is accepted agree BY CONSTRUCTION. A set advertised by one rule and matched by
 * another is the MIS-CONSTRAINED failure this phase exists to make impossible.
 *
 * <ul>
 *   <li>SHAPE (a), BARE ENUM: no enabled {@code @JsonValue} accessor. The wire form is the constant name,
 *       which is Jackson's default.</li>
 *   <li>SHAPE (b), VALUE-MAPPED ENUM: exactly one enabled, no-argument {@code @JsonValue} accessor. The
 *       wire form is what that accessor returns for each constant.</li>
 *   <li>SHAPE (c), AMBIGUOUS: more than one such accessor. The provider refuses to advertise a set, so
 *       nothing was claimed; the runtime falls back to the constant name and still names the legal values
 *       when it refuses. No claim is made and none is broken.</li>
 * </ul>
 *
 * <p>Jackson is NOT a dependency of this module and must not become one: the annotation is matched by its
 * type NAME, so an application without Jackson simply has no such annotation and takes shape (a).
 */
final class EnumWireValues {

    private static final String JSON_VALUE = "com.fasterxml.jackson.annotation.JsonValue";

    private EnumWireValues() {
    }

    /**
     * The values this enum accepts on the wire, in declaration order. Never empty for a non-empty enum,
     * and never synthesized: every element is either a declared constant's name or a value the enum's own
     * accessor returned.
     */
    static List<String> of(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null) {
            return List.of();
        }
        Optional<Method> accessor = valueAccessor(enumType);
        List<String> values = new ArrayList<>(constants.length);
        for (Object constant : constants) {
            values.add(wireValue(constant, accessor));
        }
        return List.copyOf(values);
    }

    /** The constant whose wire value equals this text, or empty when the text is not a legal value. */
    static Optional<Object> match(Class<?> enumType, String text) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || text == null) {
            return Optional.empty();
        }
        Optional<Method> accessor = valueAccessor(enumType);
        for (Object constant : constants) {
            if (text.equals(wireValue(constant, accessor))) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    private static String wireValue(Object constant, Optional<Method> accessor) {
        if (accessor.isPresent()) {
            try {
                Object value = accessor.get().invoke(constant);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // An accessor that cannot be read tells us nothing, so fall back to the constant name
                // rather than inventing a value. Never synthesize.
            }
        }
        return ((Enum<?>) constant).name();
    }

    // Shape detection: exactly one enabled, no-argument @JsonValue accessor makes the enum value-mapped.
    // Zero (shape a) or more than one (shape c) both fall back to the constant name.
    private static Optional<Method> valueAccessor(Class<?> enumType) {
        Method found = null;
        for (Method method : enumType.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (!isEnabledJsonValue(method)) {
                continue;
            }
            if (found != null) {
                return Optional.empty();
            }
            found = method;
        }
        if (found != null) {
            found.setAccessible(true);
        }
        return Optional.ofNullable(found);
    }

    private static boolean isEnabledJsonValue(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (!annotation.annotationType().getName().equals(JSON_VALUE)) {
                continue;
            }
            // @JsonValue carries an optional boolean that can switch it off; honour it rather than
            // assuming presence means enabled.
            try {
                Object enabled = annotation.annotationType().getMethod("value").invoke(annotation);
                return !(enabled instanceof Boolean b) || b;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return true;
            }
        }
        return false;
    }
}
