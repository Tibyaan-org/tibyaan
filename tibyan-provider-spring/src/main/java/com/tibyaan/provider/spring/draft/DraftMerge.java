package com.tibyaan.provider.spring.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The merge a re-run of {@code tibyan draft} performs against the existing {@code tibyan/drafts.json} (phase
 * DL-1, Volume 5 section 4.17.2): MERGES, NEVER OVERWRITES. An existing entry keeps every reviewer field; a new
 * method appends a pending entry; a target that no longer resolves is marked stale in place with its old
 * fingerprint kept; a target whose recomputed label differs from the recorded one is marked with both labels and
 * its review is RESET to pending, because a person approved a READ and the code is now a WRITE. Nothing is
 * deleted by the tool.
 *
 * <p>Output order: the extractor's deterministic order for the live candidates (matched or new), then the stale
 * entries in their existing order. Names stay unique across the merged list: an existing entry's name is the
 * reviewer's and is never changed, so a new candidate whose minted name collides takes a numeric suffix.
 */
public final class DraftMerge {

    private DraftMerge() {
    }

    /**
     * Merges freshly labeled candidates into the existing entries.
     *
     * @param fresh    the candidates as the labeler produced them, in extractor order
     * @param existing the entries read from the existing file, in file order; empty for a first run
     * @return the merged list
     */
    public static List<DraftEntry> merge(List<DraftEntry> fresh, List<DraftEntry> existing) {
        if (existing.isEmpty()) {
            return List.copyOf(fresh);
        }
        Map<String, DraftEntry> existingByTarget = new LinkedHashMap<>();
        for (DraftEntry entry : existing) {
            existingByTarget.putIfAbsent(targetKey(entry.target()), entry);
        }
        Set<DraftEntry> matched = Collections.newSetFromMap(new IdentityHashMap<>());

        List<DraftEntry> live = new ArrayList<>();
        List<Integer> unnamed = new ArrayList<>();
        Set<String> reservedNames = new HashSet<>();
        for (DraftEntry candidate : fresh) {
            DraftEntry old = existingByTarget.get(targetKey(candidate.target()));
            if (old != null && matched.add(old)) {
                live.add(carry(old, candidate));
                reservedNames.add(old.name());
            } else {
                unnamed.add(live.size());
                live.add(candidate);
            }
        }

        List<DraftEntry> stale = new ArrayList<>();
        for (DraftEntry entry : existing) {
            if (!matched.contains(entry)) {
                stale.add(markStale(entry));
                reservedNames.add(entry.name());
            }
        }

        // New candidates are named last, so a reviewer's own names (kept or stale) always win a collision.
        for (int index : unnamed) {
            DraftEntry candidate = live.get(index);
            String name = uniqueName(candidate.name(), reservedNames);
            reservedNames.add(name);
            if (!name.equals(candidate.name())) {
                live.set(index, withName(candidate, name));
            }
        }

        List<DraftEntry> merged = new ArrayList<>(live);
        merged.addAll(stale);
        return List.copyOf(merged);
    }

    // The existing entry, refreshed with the tool's current facts about the target and carrying every
    // reviewer field through. A label change resets the review and records the label it replaced; an
    // unchanged label keeps everything, the earlier previousLabel included, since the tool deletes nothing.
    private static DraftEntry carry(DraftEntry old, DraftEntry fresh) {
        boolean sameLabel = old.label() == fresh.label();
        return new DraftEntry(
                old.name(),
                fresh.target(),
                fresh.binding(),
                fresh.label(),
                fresh.reason(),
                fresh.capability(),
                fresh.returnType(),
                fresh.inputs(),
                old.description(),
                sameLabel ? old.review() : DraftEntry.PENDING,
                old.reviewedBy(),
                old.reviewedOn(),
                old.descriptionOrigin(),
                // Build decision (Volume 5 section 4.17.13): an APPROVED entry keeps the fingerprint the reviewer
                // approved, so a body change is caught as STALE_DRAFT at scan until a person re-approves it;
                // only a pending or rejected entry is re-pinned by a re-run.
                sameLabel && DraftEntry.APPROVED.equals(old.review()) ? old.fingerprint() : fresh.fingerprint(),
                false,
                sameLabel ? old.previousLabel() : old.label(),
                old.describedBy(),
                old.describedFrom(),
                // Phase Describe, DS-4 (Volume 5 section 4.15.5.2): an ai-draft description written from a fingerprint
                // the entry no longer carries is kept and marked stale, never deleted; only describe replaces it.
                DraftEntry.ORIGIN_AI_DRAFT.equals(old.descriptionOrigin()) && old.describedFrom() != null
                        && !old.describedFrom().equals(fresh.fingerprint()) ? Boolean.TRUE : null);
    }

    private static DraftEntry markStale(DraftEntry entry) {
        return new DraftEntry(entry.name(), entry.target(), entry.binding(), entry.label(), entry.reason(),
                entry.capability(), entry.returnType(), entry.inputs(), entry.description(), entry.review(),
                entry.reviewedBy(), entry.reviewedOn(), entry.descriptionOrigin(), entry.fingerprint(), true,
                entry.previousLabel(), entry.describedBy(), entry.describedFrom(), entry.descriptionStale());
    }

    private static DraftEntry withName(DraftEntry entry, String name) {
        return new DraftEntry(name, entry.target(), entry.binding(), entry.label(), entry.reason(),
                entry.capability(), entry.returnType(), entry.inputs(), entry.description(), entry.review(),
                entry.reviewedBy(), entry.reviewedOn(), entry.descriptionOrigin(), entry.fingerprint(),
                entry.stale(), entry.previousLabel(), entry.describedBy(), entry.describedFrom(),
                entry.descriptionStale());
    }

    private static String uniqueName(String minted, Set<String> reserved) {
        if (!reserved.contains(minted)) {
            return minted;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = minted + "_" + suffix;
            if (!reserved.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** The comparison key of a target: whitespace-insensitive, so a hand-spelled {@code (A,B)} matches {@code (A, B)}. */
    static String targetKey(String target) {
        return target == null ? "" : target.replaceAll("\\s+", "");
    }
}
