package com.tibyaan.provider.spring.extractors;

/**
 * The verdict of the DTO-aware matcher on one candidate method (Volume 5 section 4.2).
 *
 * <p>A match carries the view type's simple name and the structural explanation (section 4.2.3). A
 * near miss carries the located reason it failed, in the rule's terms, for the NO_BACKING_METHOD
 * diagnostics (section 4.2.4). There is no third state here: a method that fails is a near miss with
 * a reason, never a silent drop.
 *
 * @param matched     whether the method backs the read
 * @param view        the view type's simple name (match only)
 * @param explanation the structural evidence for the match (match only)
 * @param reason      the located reason it did not match (near miss only)
 */
public record MatchResult(boolean matched, String view, String explanation, String reason) {

    static MatchResult match(String view, String explanation) {
        return new MatchResult(true, view, explanation, null);
    }

    static MatchResult nearMiss(String reason) {
        return new MatchResult(false, null, null, reason);
    }
}
