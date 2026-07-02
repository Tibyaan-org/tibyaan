package com.tibyaan.backend.openapi;

import java.util.List;
import java.util.Map;

/**
 * A tiny deterministic JSON writer over ordered maps and lists, so the OpenAPI document is written
 * by hand with no serialization library and its golden-file output is exact (Volume 6 section 4).
 *
 * <p>It serializes the value graph the document builder assembles: {@link Map} (insertion order
 * preserved, so the builder controls key order), {@link List}, {@link String}, {@link Boolean},
 * {@link Integer}, and {@code null}. Anything else is a programming error and is rejected, so an
 * unintended type can never slip into the output undetected.
 */
final class Json {

    private static final String INDENT = "  ";

    private Json() {
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        out.append("\n");
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Boolean b) {
            out.append(b.toString());
        } else if (value instanceof Integer i) {
            out.append(i.toString());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map, depth);
        } else if (value instanceof List<?> list) {
            writeArray(out, list, depth);
        } else {
            throw new IllegalArgumentException("unsupported JSON value type: " + value.getClass());
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue(), depth + 1);
            out.append(i < map.size() - 1 ? ",\n" : "\n");
            i++;
        }
        indent(out, depth);
        out.append("}");
    }

    private static void writeArray(StringBuilder out, List<?> list, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, depth + 1);
            writeValue(out, list.get(i), depth + 1);
            out.append(i < list.size() - 1 ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append("]");
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append(INDENT.repeat(depth));
    }
}
