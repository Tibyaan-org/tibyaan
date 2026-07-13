package com.tibyaan.provider.spring.extractors;

import com.tibyaan.provider.spring.ProviderException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The batch-fixpoint display-name disambiguation (Volume 5 section 4.12.3). Given the qualified keys
 * (fully-qualified class names) of every entity in the model, it assigns each a display name: the
 * simple name when that simple name is unique, and a deterministic, structural, package-derived
 * disambiguation only where a simple name collides across the model.
 *
 * <p>The assignment is a pure function of the key set: order-independent and golden-stable (condition 7,
 * CLAUDE.md rule 10). It never emits a numeric suffix (condition 5), never renames a uniquely-named
 * entity (condition 6), and a residual display collision that cannot be resolved by expanding through
 * the key fails loud rather than merging two entities (conditions 4c and 8).
 *
 * <p>The caller guarantees keys are distinct (genuine duplicates and split-package clashes fail earlier,
 * section 4.12.2); this class assumes distinct keys and disambiguates their display names.
 */
public final class DisplayNames {

    private DisplayNames() {
    }

    /**
     * Assigns a display name to every key, returning key to display name. Fails loud with a
     * {@link ProviderException} naming the clashing keys when two distinct keys cannot be disambiguated
     * to distinct readable names (section 4.12.3 conditions 4c and 8).
     */
    public static Map<String, String> assign(Collection<String> keys) {
        // Group by simple name (condition 3): a group of one is immovable, a group of two or more
        // collides. TreeMap keeps the grouping a deterministic function of the key set.
        Map<String, List<String>> bySimpleName = new TreeMap<>();
        for (String key : keys) {
            bySimpleName.computeIfAbsent(simpleName(key), k -> new ArrayList<>()).add(key);
        }

        Map<String, String> displayName = new LinkedHashMap<>();
        Map<String, List<Integer>> qualifierSeq = new LinkedHashMap<>();
        Map<String, Integer> depth = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> group : bySimpleName.entrySet()) {
            List<String> groupKeys = group.getValue();
            if (groupKeys.size() == 1) {
                // Condition 1 and 6: a unique simple name is the display name, and never changes.
                displayName.put(groupKeys.get(0), group.getKey());
                continue;
            }
            int commonPrefixLen = commonPackagePrefixLength(groupKeys);
            for (String key : groupKeys) {
                List<Integer> seq = qualifierSequence(key, commonPrefixLen);
                qualifierSeq.put(key, seq);
                // Condition 4a: movable entities start at depth 1, or 0 when zero-diverging (its package
                // equals the common prefix, so it has no diverging qualifier), condition 3c.
                int start = packageSegments(key).size() == commonPrefixLen ? 0 : 1;
                depth.put(key, start);
                displayName.put(key, candidate(key, start, seq));
            }
        }

        // Condition 4, the deterministic batch fixpoint. Each round, every movable entity in a clashing
        // group whose depth is below its maximum increments by one; the increments are read from the
        // round's frozen names and applied simultaneously, so the result does not depend on any order.
        while (true) {
            Map<String, List<String>> byDisplay = new TreeMap<>();
            for (Map.Entry<String, String> entry : displayName.entrySet()) {
                byDisplay.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
            List<String> toIncrement = new ArrayList<>();
            List<String> stuckClash = null;
            for (List<String> clash : byDisplay.values()) {
                if (clash.size() < 2) {
                    continue;
                }
                boolean progressed = false;
                for (String key : clash) {
                    Integer d = depth.get(key);
                    if (d != null && d < qualifierSeq.get(key).size()) {
                        toIncrement.add(key);
                        progressed = true;
                    }
                }
                // A clash whose movable members are all at their maximum depth (or that mixes only
                // immovable and maxed-out entities) cannot be resolved: fail loud (conditions 4c, 8).
                if (!progressed && stuckClash == null) {
                    stuckClash = clash;
                }
            }
            if (stuckClash != null) {
                throw residualCollision(stuckClash, displayName);
            }
            if (toIncrement.isEmpty()) {
                return displayName;
            }
            for (String key : toIncrement) {
                int next = depth.get(key) + 1;
                depth.put(key, next);
                displayName.put(key, candidate(key, next, qualifierSeq.get(key)));
            }
        }
    }

    // Candidate(E, d): the first d qualifier segments (condition 3b), assembled in source (package)
    // order and transformed, prepended to the simple name. Candidate(E, 0) is the bare simple name.
    private static String candidate(String key, int d, List<Integer> qualifierSeq) {
        String simple = simpleName(key);
        if (d == 0) {
            return simple;
        }
        List<String> segments = packageSegments(key);
        // Select the first d qualifier indices, then assemble them in ascending (source) order.
        List<Integer> selected = new ArrayList<>(qualifierSeq.subList(0, d));
        selected.sort(Integer::compareTo);
        StringBuilder name = new StringBuilder();
        for (int index : selected) {
            name.append(token(segments.get(index)));
        }
        name.append(simple);
        return name.toString();
    }

    // The qualifier sequence (condition 3a): the diverging package segments (from the common-prefix
    // boundary toward the class name), then the common-prefix segments toward the root. Indices into
    // the key's package segments; a pure function of the key and the group's common prefix.
    private static List<Integer> qualifierSequence(String key, int commonPrefixLen) {
        int pkgLen = packageSegments(key).size();
        List<Integer> seq = new ArrayList<>();
        for (int i = commonPrefixLen; i < pkgLen; i++) {
            seq.add(i);
        }
        for (int i = commonPrefixLen - 1; i >= 0; i--) {
            seq.add(i);
        }
        return seq;
    }

    // Condition 2, the segment transform: upper-case the first character, leave every other character
    // unchanged (internal case, digits, underscores preserved). Deterministic and NOT injective; any
    // resulting name collision is caught by the global fixpoint above.
    private static String token(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }

    private static int commonPackagePrefixLength(List<String> groupKeys) {
        List<String> first = packageSegments(groupKeys.get(0));
        int len = first.size();
        for (int k = 1; k < groupKeys.size(); k++) {
            List<String> other = packageSegments(groupKeys.get(k));
            int common = 0;
            int limit = Math.min(len, other.size());
            while (common < limit && first.get(common).equals(other.get(common))) {
                common++;
            }
            len = common;
        }
        return len;
    }

    private static List<String> packageSegments(String key) {
        int lastDot = key.lastIndexOf('.');
        if (lastDot < 0) {
            return List.of();
        }
        return List.of(key.substring(0, lastDot).split("\\."));
    }

    private static String simpleName(String key) {
        int lastDot = key.lastIndexOf('.');
        return lastDot < 0 ? key : key.substring(lastDot + 1);
    }

    private static ProviderException residualCollision(List<String> keys, Map<String, String> displayName) {
        String display = displayName.get(keys.get(0));
        return new ProviderException("residual display collision: the entities " + keys
                + " cannot be disambiguated to distinct readable names; they all resolve to display name '"
                + display + "' even at full qualification (Volume 5 section 4.12.3, conditions 4c and 8). "
                + "This is a structural limit, not a mis-bind: no output is emitted for this model rather "
                + "than merging two distinct entities. Rename one of the colliding classes to resolve it.");
    }
}
