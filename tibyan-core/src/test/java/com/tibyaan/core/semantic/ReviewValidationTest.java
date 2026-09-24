package com.tibyaan.core.semantic;

import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase DL-1 (Volume 4 section 3.10, rules 12 and 13): a reviewed capability is complete and prefixed, a write
 * or action never ships without a named reviewer, and the kind follows the label.
 */
class ReviewValidationTest {

    private final SemanticValidator validator = new SemanticValidator();

    private static Review review(ReviewLabel label) {
        return new Review(label, "a.person", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:abc");
    }

    private static CapabilityModel model(Capability... capabilities) {
        return new CapabilityModel("m", "0.1", List.of(), List.of(capabilities), List.of(), Metadata.empty());
    }

    @Test
    void aCompleteReviewedWriteIsValid() {
        Capability write = Capability.reviewed("reviewed_createNote", Map.of("title", Primitive.STRING),
                Primitive.LONG, false, new BindingRef("NoteService#create"), List.of(), Map.of(),
                review(ReviewLabel.WRITE));

        assertThat(write.kind()).isEqualTo(CapabilityKind.WRITE);
        assertThat(write.readOnly()).isFalse();
        assertThat(validator.validate(model(write)).isValid()).isTrue();
    }

    @Test
    void aReviewedReadIsReadOnlyAndAnUnknownIsAnAction() {
        Capability read = Capability.reviewed("reviewed_listNotes", Map.of(), Primitive.STRING, true,
                new BindingRef("NoteService#list"), List.of(), Map.of(), review(ReviewLabel.READ));
        Capability action = Capability.reviewed("reviewed_notify", Map.of("id", Primitive.LONG), Primitive.STRING,
                false, new BindingRef("NoteService#notify"), List.of(), Map.of(), review(ReviewLabel.UNKNOWN));

        assertThat(read.kind()).isEqualTo(CapabilityKind.READ);
        assertThat(read.readOnly()).isTrue();
        assertThat(action.kind()).isEqualTo(CapabilityKind.ACTION);
        assertThat(action.readOnly()).isFalse();
        assertThat(validator.validate(model(read, action)).isValid()).isTrue();
    }

    @Test
    void rule12_aWriteWithoutAReviewIsRefused() {
        Capability bare = Capability.of("createNote", CapabilityKind.WRITE, Map.of(), Primitive.LONG, false, false,
                new BindingRef("NoteService#create"), List.of(), null);

        assertThat(validator.validate(model(bare)).errors())
                .anyMatch(e -> e.contains("createNote") && e.contains("no review") && e.contains("rule 12"));
    }

    @Test
    void rule12_anIncompleteReviewIsRefused() {
        Review blankReviewer = new Review(ReviewLabel.READ, " ", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:abc");
        Review badOrigin = new Review(ReviewLabel.READ, "a.person", "2026-09-23", "guess", "sha256:abc");
        Capability one = Capability.reviewed("reviewed_a", Map.of(), Primitive.STRING, false,
                new BindingRef("S#a"), List.of(), Map.of(), blankReviewer);
        Capability two = Capability.reviewed("reviewed_b", Map.of(), Primitive.STRING, false,
                new BindingRef("S#b"), List.of(), Map.of(), badOrigin);

        List<String> errors = validator.validate(model(one, two)).errors();
        assertThat(errors).anyMatch(e -> e.contains("reviewed_a") && e.contains("incomplete review"));
        assertThat(errors).anyMatch(e -> e.contains("reviewed_b") && e.contains("incomplete review"));
    }

    @Test
    void rule12_theKindMustFollowTheLabel() {
        Capability mismatch = new Capability("reviewed_x", CapabilityKind.READ, Map.of(), Primitive.STRING, false,
                true, new BindingRef("S#x"), List.of(), null, Map.of(), review(ReviewLabel.WRITE), java.util.Set.of());

        assertThat(validator.validate(model(mismatch)).errors())
                .anyMatch(e -> e.contains("reviewed_x") && e.contains("RV-H"));
    }

    @Test
    void rule13_thePrefixIsReservedBothWays() {
        Capability unprefixed = Capability.reviewed("listNotes", Map.of(), Primitive.STRING, true,
                new BindingRef("NoteService#list"), List.of(), Map.of(), review(ReviewLabel.READ));
        Capability impostor = Capability.read("reviewed_getNote", Map.of("id", Primitive.LONG), Primitive.STRING,
                new BindingRef("NoteService#get"));

        List<String> errors = validator.validate(model(unprefixed, impostor)).errors();
        assertThat(errors).anyMatch(e -> e.contains("listNotes") && e.contains("lacks the 'reviewed_'"));
        assertThat(errors).anyMatch(e -> e.contains("reviewed_getNote") && e.contains("has no review"));
    }

    @Test
    void aModelWithoutReviewsIsUntouched() {
        Capability verified = Capability.read("getNote", Map.of("id", Primitive.LONG), Primitive.STRING,
                new BindingRef("NoteService#get"));

        assertThat(verified.hasReview()).isFalse();
        assertThat(validator.validate(model(verified)).isValid()).isTrue();
    }
}
