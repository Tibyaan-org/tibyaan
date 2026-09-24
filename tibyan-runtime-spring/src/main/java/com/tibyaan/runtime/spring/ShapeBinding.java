package com.tibyaan.runtime.spring;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Phase DL-2b at the runtime (Volume 7 section 13 item 2): binds a JSON object to the bound method's declared
 * parameter class through its manifest shape. The object is first WALKED against the shape: an unknown
 * property refuses (decision D7, never dropped), every closed set is enforced naming the legal values, a
 * multiple field must be an array, a nested shape is walked in turn. Then the object is bound with Jackson,
 * unknown properties failing there as well, and an enum field's wire value is turned into the constant the
 * class declares, exactly as EI-R does for a top-level enum input. A skipped field is never in the shape, so
 * it is never set.
 */
final class ShapeBinding {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private ShapeBinding() {
    }

    /** Binds one structured argument, or a JSON array of them when the input is multiple. */
    static Object bind(Object value, ShapeDescriptor shape, Map<String, ShapeDescriptor> shapesByName,
                       Class<?> declaredType, Type genericType, boolean multiple, String inputName) {
        if (value == null) {
            return null;
        }
        if (!multiple) {
            return bindOne(value, shape, shapesByName, declaredType, inputName);
        }
        if (!(value instanceof Collection<?> elements)) {
            throw new IllegalArgumentException("input '" + inputName + "' is multiple and must be a JSON array of "
                    + shape.name() + " objects, but the value was " + value.getClass().getSimpleName());
        }
        if (!(genericType instanceof ParameterizedType parameterized)
                || parameterized.getActualTypeArguments().length != 1
                || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> elementClass)) {
            throw new IllegalArgumentException("input '" + inputName + "' is multiple but the bound parameter '"
                    + genericType.getTypeName() + "' names no single element class; the adapter never guesses");
        }
        Collection<Object> built = declaredType.isAssignableFrom(ArrayList.class)
                ? new ArrayList<>() : new LinkedHashSet<>();
        int index = 0;
        for (Object element : elements) {
            built.add(bindOne(element, shape, shapesByName, elementClass, inputName + "[" + index + "]"));
            index++;
        }
        return built;
    }

    private static Object bindOne(Object value, ShapeDescriptor shape, Map<String, ShapeDescriptor> shapesByName,
                                  Class<?> declaredClass, String where) {
        if (!(value instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("input '" + where + "' is a " + shape.name()
                    + " object and must be a JSON object, but the value was " + value.getClass().getSimpleName());
        }
        Map<String, Object> checked = walk(object, shape, shapesByName, declaredClass, where);
        try {
            return MAPPER.convertValue(checked, declaredClass);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("input '" + where + "' could not be bound to " + declaredClass.getName()
                    + ": " + e.getMessage(), e);
        }
    }

    // The walk: every property is a field of the shape (D7), a closed set is enforced and rewritten to the
    // class's constant name, a multiple field is an array, a nested shape recurses with the field's class.
    private static Map<String, Object> walk(Map<?, ?> object, ShapeDescriptor shape,
                                            Map<String, ShapeDescriptor> shapesByName, Class<?> declaredClass,
                                            String where) {
        Map<String, ShapeDescriptor.ShapeFieldDescriptor> fields = new LinkedHashMap<>();
        for (ShapeDescriptor.ShapeFieldDescriptor field : shape.fields()) {
            fields.put(field.name(), field);
        }
        Map<String, Object> checked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            String property = String.valueOf(entry.getKey());
            ShapeDescriptor.ShapeFieldDescriptor field = fields.get(property);
            if (field == null) {
                throw new IllegalArgumentException("input '" + where + "' carries '" + property + "', which is not a"
                        + " field of " + shape.name() + " (fields: " + String.join(", ", fields.keySet())
                        + (shape.partial().isEmpty() ? "" : "; not modeled: " + String.join(", ", shape.partial()))
                        + "); the adapter never drops a property silently");
            }
            Object raw = entry.getValue();
            String at = where + "." + property;
            if (raw == null) {
                checked.put(property, null);
                continue;
            }
            if (field.multiple()) {
                if (!(raw instanceof Collection<?> elements)) {
                    throw new IllegalArgumentException("input '" + at + "' is multiple and must be a JSON array, but"
                            + " the value was " + raw.getClass().getSimpleName());
                }
                List<Object> items = new ArrayList<>();
                int index = 0;
                for (Object element : elements) {
                    items.add(walkValue(element, field, shapesByName, elementClassOf(declaredClass, property),
                            at + "[" + index + "]"));
                    index++;
                }
                checked.put(property, items);
            } else {
                checked.put(property, walkValue(raw, field, shapesByName, fieldClassOf(declaredClass, property), at));
            }
        }
        return checked;
    }

    private static Object walkValue(Object raw, ShapeDescriptor.ShapeFieldDescriptor field,
                                    Map<String, ShapeDescriptor> shapesByName, Class<?> fieldClass, String at) {
        if (field.type().startsWith("SHAPE_REF(")) {
            String name = field.type().substring("SHAPE_REF(".length(), field.type().length() - 1);
            ShapeDescriptor nested = shapesByName.get(name);
            if (nested == null) {
                throw new IllegalStateException("input '" + at + "' references shape '" + name + "', which the"
                        + " manifest does not carry");
            }
            if (!(raw instanceof Map<?, ?> object)) {
                throw new IllegalArgumentException("input '" + at + "' is a " + name + " object and must be a JSON"
                        + " object, but the value was " + raw.getClass().getSimpleName());
            }
            return walk(object, nested, shapesByName, fieldClass, at);
        }
        if (!field.values().isEmpty()) {
            String text = String.valueOf(raw).trim();
            if (!field.values().contains(text)) {
                throw new IllegalArgumentException("input '" + at + "' has value '" + text + "', which is not one of"
                        + " the values it accepts: " + String.join(", ", field.values()));
            }
            // The closed set names the WIRE values; the class may map them through @JsonValue or declare them
            // as constant names. Jackson binds either from the wire form, so the text is passed through.
            return text;
        }
        return raw;
    }

    // The declared class of a field, for the nested walk; Object when the class does not declare it, in
    // which case Jackson's own binding is the authority and the walk still checks the shape.
    private static Class<?> fieldClassOf(Class<?> declaredClass, String property) {
        if (declaredClass == null) {
            return Object.class;
        }
        for (Class<?> c = declaredClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(property).getType();
            } catch (NoSuchFieldException ignored) {
                // keep looking up the hierarchy
            }
        }
        return Object.class;
    }

    private static Class<?> elementClassOf(Class<?> declaredClass, String property) {
        if (declaredClass == null) {
            return Object.class;
        }
        for (Class<?> c = declaredClass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Type generic = c.getDeclaredField(property).getGenericType();
                if (generic instanceof ParameterizedType parameterized
                        && parameterized.getActualTypeArguments().length == 1
                        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
                    return element;
                }
                return Object.class;
            } catch (NoSuchFieldException ignored) {
                // keep looking up the hierarchy
            }
        }
        return Object.class;
    }
}
