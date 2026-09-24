package com.tibyaan.provider.spring.extractors;

import java.util.Optional;
import java.util.Set;

/**
 * Phase DL-2a, rule R1 (Volume 5 section 4.19.2): a parameter declared {@code List<T>}, {@code Set<T>},
 * {@code Collection<T>} or {@code Iterable<T>} binds as the input T carried as an array on the wire. This
 * helper decides whether a declared type IS such a collection and names its element type; whether T itself
 * binds (a modeled primitive or an in-tree enum) is the caller's question, answered by the same rule that
 * binds a plain parameter.
 *
 * <p>Everything else the source can spell as a collection is refused by name, never guessed: a raw
 * collection (no element type), a wildcard element ({@code ? extends X}), a nested collection, an array,
 * and a {@code Map}, which is not a collection of one type.
 */
public final class CollectionParameter {

    private static final Set<String> COLLECTION_TYPES = Set.of("List", "Set", "Collection", "Iterable");

    /** The outcome: exactly one of element or refusal is present. */
    public record Outcome(String element, String refusal) {

        public boolean isCollection() {
            return element != null || refusal != null;
        }

        public boolean isRefused() {
            return refusal != null;
        }

        static Outcome notACollection() {
            return new Outcome(null, null);
        }

        static Outcome element(String element) {
            return new Outcome(element, null);
        }

        static Outcome refused(String refusal) {
            return new Outcome(null, refusal);
        }
    }

    private CollectionParameter() {
    }

    /** Classifies a parameter type as written in source. */
    public static Outcome classify(String declaredType) {
        if (declaredType == null) {
            return Outcome.notACollection();
        }
        String type = declaredType.trim();
        if (type.endsWith("[]")) {
            return Outcome.refused("an array parameter is not a modeled collection (section 4.19.2, R1)");
        }
        int open = type.indexOf('<');
        String outer = lastSegment(open < 0 ? type : type.substring(0, open));
        if (!COLLECTION_TYPES.contains(outer)) {
            return Outcome.notACollection();
        }
        if (open < 0 || !type.endsWith(">")) {
            return Outcome.refused("a raw " + outer + " names no element type, so the tool cannot say what"
                    + " the array carries (section 4.19.2, R1)");
        }
        String element = type.substring(open + 1, type.length() - 1).trim();
        if (element.startsWith("?")) {
            return Outcome.refused("the element type '" + element + "' is a wildcard, which names no one"
                    + " type (section 4.19.2, R1)");
        }
        if (element.contains("<") || element.endsWith("[]")) {
            return Outcome.refused("the element type '" + element + "' is itself a collection or an array;"
                    + " a nested collection is not modeled (section 4.19.2, R1)");
        }
        if (element.contains(",")) {
            return Outcome.refused("'" + type + "' carries more than one type argument (section 4.19.2, R1)");
        }
        return Outcome.element(element);
    }

    /** Fully qualified spellings ({@code java.util.List<T>}) match by their last segment. */
    public static Optional<String> elementOf(String declaredType) {
        Outcome outcome = classify(declaredType);
        return Optional.ofNullable(outcome.element());
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
