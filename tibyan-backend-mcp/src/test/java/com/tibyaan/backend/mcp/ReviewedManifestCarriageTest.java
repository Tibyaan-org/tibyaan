package com.tibyaan.backend.mcp;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.BindingRef;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Metadata;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carriage of a reviewed capability on the MCP manifest surface (phase DL-1, Volume 6 section 3.9): the
 * class sentence leads, the reviewer's words follow verbatim, the kind and the review object are present
 * exactly on a reviewed tool, and a verified or attested tool gains none of it (fixture RV-12). The golden
 * case in {@link McpBackendGoldenTest} pins the whole manifest; this test pins the rules one by one, so a
 * drift names the rule it broke.
 */
class ReviewedManifestCarriageTest {

    private static Review review(ReviewLabel label) {
        return new Review(label, "a.elseginy", "2026-09-23", Review.ORIGIN_HUMAN, "sha256:r1");
    }

    private static CapabilityModel model(Capability... capabilities) {
        Entity product = new Entity("Product", "Product",
                List.of(Field.of("id", Primitive.LONG, false)), List.of(), Metadata.empty());
        return new CapabilityModel("shop", "0.1", List.of(product), List.of(capabilities), List.of(),
                Metadata.empty());
    }

    private static Map<String, Type> inputs(String name, Type type) {
        Map<String, Type> inputs = new LinkedHashMap<>();
        inputs.put(name, type);
        return inputs;
    }

    @Test
    void aReviewedWriteCarriesItsKindTierReviewAndClassLeadingDescription() {
        Capability write = Capability.reviewed("reviewed_createProduct", inputs("name", Primitive.STRING),
                Primitive.LONG, false, new BindingRef("ProductService#create"),
                List.of(new Annotation("description",
                        List.of("Creates a product with the given name and returns its id."))),
                Map.of(), review(ReviewLabel.WRITE));

        String json = ManifestWriter.toJson(model(write));

        // The fields ride right after the binding, in this order, and the review object is character-exact.
        assertThat(json).contains("      \"binding\": \"ProductService#create\",\n"
                + "      \"kind\": \"WRITE\",\n"
                + "      \"tier\": \"reviewed\",\n"
                + "      \"review\": {\n"
                + "        \"label\": \"WRITE\",\n"
                + "        \"reviewedBy\": \"a.elseginy\",\n"
                + "        \"reviewedOn\": \"2026-09-23\",\n"
                + "        \"descriptionOrigin\": \"human\"\n"
                + "      },\n"
                + "      \"output\": \"LONG\",\n");
        // The class is the first sentence; the reviewer's sentence follows verbatim.
        assertThat(json).contains("\"description\": \"Reviewed by a.elseginy (human), not tool-verified; the "
                + "tool labeled it WRITE. Creates a product with the given name and returns its id.\"");
    }

    @Test
    void anUnknownDraftIsAnActionAndAVoidOneSaysItReturnsNothing() {
        Capability action = Capability.reviewed("reviewed_reindexProducts", new LinkedHashMap<>(),
                Primitive.STRING, false, new BindingRef("ProductService#reindex"),
                List.of(Annotation.flag("void"),
                        new Annotation("description", List.of("Rebuilds the product search index."))),
                Map.of(), new Review(ReviewLabel.UNKNOWN, "a.elseginy", "2026-09-23",
                        Review.ORIGIN_AI_DRAFT, "sha256:r3"));

        String json = ManifestWriter.toJson(model(action));

        assertThat(json).contains("\"kind\": \"ACTION\"");
        assertThat(json).contains("\"label\": \"UNKNOWN\"");
        assertThat(json).contains("\"descriptionOrigin\": \"ai-draft\"");
        // The void marker rides after the placeholder output; the description says it in prose.
        assertThat(json).contains("      \"output\": \"STRING\",\n      \"void\": true,\n");
        assertThat(json).contains("\"description\": \"Reviewed by a.elseginy (ai-draft), not tool-verified; the "
                + "tool labeled it UNKNOWN. Rebuilds the product search index. (returns nothing)\"");
    }

    @Test
    void aReviewedReadIsAReadWithNoKindFieldAndAnOpaqueOutputIsNamed() {
        Capability read = Capability.reviewed("reviewed_getProductStats", inputs("id", Primitive.LONG),
                Primitive.STRING, false, new BindingRef("ProductService#statsFor"),
                List.of(new Annotation("description", List.of("Returns sales statistics for the product.")),
                        new Annotation("opaqueOutput", List.of("ProductStatsDto"))),
                Map.of(), review(ReviewLabel.READ));

        String json = ManifestWriter.toJson(model(read));

        // READ is the default kind, so no kind field, exactly as on every verified tool; the tier still rides.
        assertThat(json).doesNotContain("\"kind\"");
        assertThat(json).contains("\"tier\": \"reviewed\"");
        assertThat(json).contains("\"label\": \"READ\"");
        assertThat(json).contains("      \"output\": \"STRING\",\n      \"opaqueOutput\": \"ProductStatsDto\",\n");
        assertThat(json).contains("Returns sales statistics for the product. Returns ProductStatsDto; its "
                + "field shape is not modeled.\"");
    }

    @Test
    void withoutAReviewerSentenceTheClassSentenceStandsAloneAndNothingIsInvented() {
        Capability bare = Capability.reviewed("reviewed_touchProduct", inputs("id", Primitive.LONG),
                Primitive.STRING, false, new BindingRef("ProductService#touch"), List.of(), Map.of(),
                review(ReviewLabel.WRITE));

        String json = ManifestWriter.toJson(model(bare));

        assertThat(json).contains("\"description\": \"Reviewed by a.elseginy (human), not tool-verified; the "
                + "tool labeled it WRITE.\"");
    }

    @Test
    void anEnumConstrainedReviewedInputRendersItsClosedSetAsToday() {
        Capability read = Capability.reviewed("reviewed_findProductsByStatus",
                inputs("status", Primitive.STRING), Type.entityRef("Product"), true,
                new BindingRef("ProductService#findByStatus"), List.of(),
                Map.of("status", List.of("ACTIVE", "DISCONTINUED")), review(ReviewLabel.READ));

        String json = ManifestWriter.toJson(model(read));

        assertThat(json).contains("\"enum\": [\"ACTIVE\", \"DISCONTINUED\"]");
        assertThat(json).contains("\"outputMultiple\": true");
    }

    @Test
    void rv12_aVerifiedToolGainsNoKindTierOrReviewField() {
        Capability verified = Capability.read("getProduct", inputs("id", Primitive.LONG),
                Type.entityRef("Product"), new BindingRef("ProductService#findById"));

        String json = ManifestWriter.toJson(model(verified));

        assertThat(json).doesNotContain("\"kind\"");
        assertThat(json).doesNotContain("\"tier\"");
        assertThat(json).doesNotContain("\"review\"");
        assertThat(json).doesNotContain("\"void\"");
        assertThat(json).doesNotContain("\"opaqueOutput\"");
        assertThat(json).doesNotContain("Reviewed by");
    }
}
