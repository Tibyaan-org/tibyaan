package com.tibyaan.provider.spring.extractors;

import com.tibyaan.provider.spring.ProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * The batch-fixpoint display-name disambiguation (Volume 5 section 4.12.3), tested as a pure function
 * of the key set. Covers the pre-registered fixtures that exercise the algorithm directly (EI-1, EI-2,
 * EI-7 to EI-10, EI-13, EI-14), including the two adversarial-review counterexamples the batch form
 * exists to make deterministic. All names are synthetic (Volume 5 section 4.12; private-benchmark
 * redaction).
 */
class DisplayNamesTest {

    @Test
    void ei2_uniqueSimpleNamesKeepTheirNames() {
        Map<String, String> names = DisplayNames.assign(List.of(
                "com.a.model.Product", "com.b.model.Order"));
        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(
                "com.a.model.Product", "Product",
                "com.b.model.Order", "Order"));
    }

    @Test
    void ei1_twoSameNamedEntitiesDisambiguateToTheWorkedExample() {
        Map<String, String> names = DisplayNames.assign(List.of(
                "example.crm.model.entity.Note", "example.support.model.entity.Note"));
        assertThat(names).contains(
                entry("example.crm.model.entity.Note", "CrmNote"),
                entry("example.support.model.entity.Note", "SupportNote"));
    }

    @Test
    void ei7_threeCollidersTwoSharingTheirFirstDivergingSegmentExpandMinimally() {
        Map<String, String> names = DisplayNames.assign(List.of(
                "a.sales.retail.Order", "a.sales.wholesale.Order", "a.audit.Order"));
        assertThat(names).contains(
                entry("a.audit.Order", "AuditOrder"),
                entry("a.sales.retail.Order", "SalesRetailOrder"),
                entry("a.sales.wholesale.Order", "SalesWholesaleOrder"));
    }

    @Test
    void ei13_zeroDivergingEntityKeepsTheBareNameAndItsPeerDisambiguates() {
        Map<String, String> names = DisplayNames.assign(List.of("a.Note", "a.b.Note"));
        assertThat(names).contains(
                entry("a.Note", "Note"),
                entry("a.b.Note", "BNote"));
    }

    @Test
    void ei8_disambiguatedNameClashingWithAUniqueNameExpandsAndNeverRenamesTheUnique() {
        Map<String, String> names = DisplayNames.assign(List.of(
                "x.order.Offer", "x.discount.Offer", "x.catalog.OrderOffer"));
        // The unique entity keeps its simple name; the colliding one expands past the clash.
        assertThat(names).contains(entry("x.catalog.OrderOffer", "OrderOffer"));
        assertThat(names.get("x.order.Offer")).isNotEqualTo("OrderOffer");
        assertThat(names.get("x.discount.Offer")).isEqualTo("DiscountOffer");
        // All display names are globally unique.
        assertThat(names.values()).doesNotHaveDuplicates();
    }

    @Test
    void ei9_disambiguatedNameClashingWithAUniqueNameThatCannotExpandFailsLoud() {
        assertThatThrownBy(() -> DisplayNames.assign(List.of(
                "order.Offer", "discount.Offer", "catalog.OrderOffer")))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("residual display collision")
                .hasMessageContaining("order.Offer")
                .hasMessageContaining("catalog.OrderOffer");
    }

    @Test
    void ei10_transformFoldWithNoFurtherSegmentFailsLoudRatherThanMerge() {
        // "order" and "Order" both transform to the token "Order"; with no further segment to separate
        // them the fixpoint fails loud (it never silently merges two distinct keys).
        assertThatThrownBy(() -> DisplayNames.assign(List.of("a.order.Note", "a.Order.Note")))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("residual display collision");
    }

    @Test
    void ei14_mutuallyCollidingMovableEntitiesResolveDeterministically() {
        // Review counterexample A: an Offer-collision set and an XOffer-collision set whose names clash
        // across sets, one member zero-diverging. The batch fixpoint expands both simultaneously to a
        // single order-independent assignment.
        Map<String, String> names = DisplayNames.assign(List.of(
                "a.x.Offer", "a.y.Offer", "b.XOffer", "b.q.XOffer"));
        assertThat(names.values()).doesNotHaveDuplicates();
        assertThat(names).contains(
                entry("a.x.Offer", "AXOffer"),
                entry("a.y.Offer", "YOffer"),
                entry("b.XOffer", "BXOffer"),
                entry("b.q.XOffer", "QXOffer"));
    }

    @Test
    void ei14b_mutualCollisionBlockedByAUniqueNameFailsLoudDeterministically() {
        // Review counterexample B: the same shape plus a unique entity that occupies the only name the
        // zero-diverging member could expand to. The outcome is a deterministic fail-loud, not a
        // scan-order-dependent flip between emit and fail.
        assertThatThrownBy(() -> DisplayNames.assign(List.of(
                "a.x.Offer", "a.y.Offer", "b.XOffer", "b.q.XOffer", "z.BXOffer")))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("residual display collision");
    }

    @Test
    void assignmentIsOrderIndependent() {
        // CLAUDE.md rule 10: the same key set yields the same assignment regardless of input order.
        List<String> keys = List.of(
                "a.x.Offer", "a.y.Offer", "b.XOffer", "b.q.XOffer", "com.a.model.Product");
        List<String> reversedKeys = new java.util.ArrayList<>(keys);
        java.util.Collections.reverse(reversedKeys);
        Map<String, String> forward = DisplayNames.assign(keys);
        Map<String, String> reversed = DisplayNames.assign(reversedKeys);
        assertThat(reversed).containsExactlyInAnyOrderEntriesOf(forward);
    }
}
