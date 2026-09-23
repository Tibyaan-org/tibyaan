package com.tibyaan.provider.spring.draft;

import com.tibyaan.provider.spring.ScanReport;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of {@code tibyan draft} (spike DL-0): every draft candidate with its label, the label census, and
 * the scan the labels were derived from.
 *
 * @param drafts the candidates in the extractor's deterministic order
 * @param counts the census, one entry per label in enum order, zero included, so the file always shows all five
 * @param scan   the survey the VERIFIED and ATTESTED labels were matched against
 */
public record DraftReport(List<DraftEntry> drafts, Map<String, Integer> counts, ScanReport scan) {

    public DraftReport {
        drafts = drafts == null ? List.of() : List.copyOf(drafts);
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts == null ? count(drafts) : counts));
    }

    /** The label census of a draft list, every label present, in enum order. */
    public static Map<String, Integer> count(List<DraftEntry> drafts) {
        Map<DraftLabel, Integer> byLabel = new EnumMap<>(DraftLabel.class);
        for (DraftLabel label : DraftLabel.values()) {
            byLabel.put(label, 0);
        }
        for (DraftEntry draft : drafts) {
            byLabel.merge(draft.label(), 1, Integer::sum);
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        byLabel.forEach((label, n) -> ordered.put(label.name(), n));
        return ordered;
    }
}
