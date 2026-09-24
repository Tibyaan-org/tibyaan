package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Review;
import com.tibyaan.core.ir.ReviewLabel;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.core.semantic.ValidationResult;
import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.draft.DraftEntry;
import com.tibyaan.provider.spring.draft.DraftIntake;
import com.tibyaan.provider.spring.draft.DraftLabel;
import com.tibyaan.provider.spring.draft.DraftMerge;
import com.tibyaan.provider.spring.draft.DraftRefusal;
import com.tibyaan.provider.spring.draft.DraftRefusal.Cause;
import com.tibyaan.provider.spring.draft.DraftReport;
import com.tibyaan.provider.spring.draft.DraftsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase DL-1, the provider half (Volume 5 section 4.17): the record's stamp, the merge, and the intake of
 * approved drafts into reviewed capabilities, end to end through the provider. The fixture is copied to a temp
 * source root so a real {@code tibyan/drafts.json} can be drafted, edited as a reviewer would, and scanned.
 * Fixtures RV-1 to RV-9, RV-11 and RV-12 of section 4.17.10 (RV-2's runtime gate, RV-10's core rules, and the
 * backend surfaces are proven in their own modules).
 */
class DraftReviewProviderTest {

    private static final Path FIXTURE =
            Path.of("src", "test", "resources", "fixtures", "draft-review", "DraftReview.java");
    private static final String SVC = "com.example.review.InvoiceServiceImpl#";
    private static final String REVIEWER = "a.reviewer";
    private static final String DATE = "2026-09-23";

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- the record: the stamp and the file shape ----------

    @Test
    void theStampEqualsTheAttestationFingerprintOfTheSameTarget(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftReport drafted = provider.draft(new ProviderInput(root));

        for (DraftEntry entry : drafted.drafts()) {
            Optional<String> attestation = provider.attestationFingerprint(new ProviderInput(root), entry.target());
            assertThat(attestation).as("fingerprint of " + entry.target()).isPresent();
            assertThat(entry.fingerprint()).as("stamp of " + entry.target()).isEqualTo(attestation.get());
            assertThat(entry.fingerprint()).startsWith("sha256:");
            assertThat(entry.descriptionOrigin()).isEqualTo(DraftEntry.ORIGIN_HUMAN);
            assertThat(entry.review()).isEqualTo(DraftEntry.PENDING);
            assertThat(entry.stale()).isFalse();
        }
    }

    @Test
    void aFreshFileIsDl0sShapePlusTheOriginAndTheStamp(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftReport drafted = provider.draft(new ProviderInput(root));

        String rendered = DraftsFile.render(drafted);
        assertThat(rendered).doesNotContain("\"reviewedBy\"").doesNotContain("\"reviewedOn\"")
                .doesNotContain("\"previousLabel\"").doesNotContain("\"stale\"")
                .doesNotContain("\"capability\" : null").doesNotContain("\"approved\"");
        assertThat(rendered).contains("\"capability\" : \"getInvoice\"")
                .contains("\"descriptionOrigin\" : \"human\"").contains("\"fingerprint\" : \"sha256:");
        // Field order is the record order: DL-0's fields first, then the DL-1 review fields.
        assertThat(rendered.indexOf("\"review\" : \"pending\""))
                .isLessThan(rendered.indexOf("\"descriptionOrigin\""));
        assertThat(rendered.indexOf("\"descriptionOrigin\"")).isLessThan(rendered.indexOf("\"fingerprint\""));

        Path file = DraftsFile.defaultLocation(root);
        DraftsFile.write(file, drafted);
        assertThat(DraftsFile.read(file)).isEqualTo(drafted.drafts());
    }

    @Test
    void theReaderToleratesUnknownFieldsAndOmittedOnes(@TempDir Path root) throws IOException {
        Path file = root.resolve("drafts.json");
        Files.writeString(file, "{\"version\":1,\"extra\":true,\"drafts\":[{\"name\":\"x\",\"target\":\"a.B#c()\","
                + "\"binding\":\"B#c\",\"label\":\"READ\",\"reason\":\"r\",\"returnType\":\"void\",\"inputs\":[],"
                + "\"description\":\"d\",\"review\":\"approved\",\"typo\":1}]}");

        List<DraftEntry> read = DraftsFile.read(file);

        assertThat(read).hasSize(1);
        DraftEntry entry = read.get(0);
        assertThat(entry.capability()).isNull();
        assertThat(entry.reviewedBy()).isNull();
        assertThat(entry.descriptionOrigin()).isEqualTo("human");
        assertThat(entry.fingerprint()).isNull();
        assertThat(entry.stale()).isFalse();
        assertThat(entry.previousLabel()).isNull();
        assertThat(DraftsFile.read(root.resolve("absent.json"))).isEmpty();
    }

    // ---------- RV-1 to RV-3: the three labels become the three kinds ----------

    @Test
    void rv1_anApprovedReadBecomesAReviewedReadCapability(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, approved(summarize, "Summarizes an invoice in the given currency."));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability reviewed = capability(report, "reviewed_InvoiceServiceImpl_summarize");
        assertThat(reviewed.kind()).isEqualTo(CapabilityKind.READ);
        assertThat(reviewed.readOnly()).isTrue();
        assertThat(reviewed.binding().value()).isEqualTo("com.example.review.InvoiceServiceImpl#summarize");
        assertThat(reviewed.inputs()).containsExactly(
                Map.entry("id", Primitive.LONG), Map.entry("currency", Primitive.STRING));
        // RV-F, decision D5: the DTO output is the permissive object, disclosed by name, never a refusal.
        assertThat(reviewed.output()).isEqualTo(Primitive.STRING);
        assertThat(reviewed.outputMultiple()).isFalse();
        assertThat(reviewed.annotations()).containsExactly(
                new Annotation(DraftIntake.DESCRIPTION_ANNOTATION,
                        List.of("Summarizes an invoice in the given currency.")),
                new Annotation(DraftIntake.OPAQUE_OUTPUT_ANNOTATION, List.of("InvoiceSummary")));
        assertThat(reviewed.hasAttestation()).isFalse();

        Review review = reviewed.review();
        assertThat(review.label()).isEqualTo(ReviewLabel.READ);
        assertThat(review.reviewedBy()).isEqualTo(REVIEWER);
        assertThat(review.reviewedOn()).isEqualTo(DATE);
        assertThat(review.descriptionOrigin()).isEqualTo("human");
        assertThat(review.fingerprint()).isEqualTo(summarize.fingerprint());
        assertThat(review.complete()).isTrue();

        // Counted apart, never summed: discovered is still the one verified read.
        assertThat(report.reviewedCount()).isEqualTo(1);
        assertThat(report.draftRefusals()).isEmpty();
        assertThat(report.draftsPending()).isZero();
        assertThat(report.discoveredCount()).isEqualTo(1);
        assertThat(report.attestedCount()).isZero();
        assertValid(report);
    }

    @Test
    void rv1_aReviewerRenamedEntryKeepsItsNameUnderThePrefix(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, edit(approved(summarize, "Summarizes an invoice.")).name("invoiceSummary").build());

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(capability(report, "reviewed_invoiceSummary").review().label()).isEqualTo(ReviewLabel.READ);
        assertValid(report);
    }

    @Test
    void rv2_anApprovedWriteBecomesKindWrite(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry open = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_open");
        assertThat(open.label()).isEqualTo(DraftLabel.WRITE);
        writeDrafts(root, approved(open, "Opens a new invoice with the given number."));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability reviewed = capability(report, "reviewed_InvoiceServiceImpl_open");
        assertThat(reviewed.kind()).isEqualTo(CapabilityKind.WRITE);
        assertThat(reviewed.readOnly()).isFalse();
        assertThat(reviewed.inputs()).containsExactly(Map.entry("number", Primitive.STRING));
        assertThat(reviewed.output()).isEqualTo(Type.entityRef("Invoice"));
        assertThat(reviewed.outputMultiple()).isFalse();
        assertThat(reviewed.review().label()).isEqualTo(ReviewLabel.WRITE);
        assertValid(report);
    }

    @Test
    void rv3_anApprovedUnknownVoidMethodBecomesKindAction(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry reindex = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_reindex");
        assertThat(reindex.label()).isEqualTo(DraftLabel.UNKNOWN);
        writeDrafts(root, approved(reindex, "Rebuilds the search index entry of an invoice."));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability reviewed = capability(report, "reviewed_InvoiceServiceImpl_reindex");
        assertThat(reviewed.kind()).isEqualTo(CapabilityKind.ACTION);
        assertThat(reviewed.readOnly()).isFalse();
        assertThat(reviewed.inputs()).containsExactly(Map.entry("id", Primitive.LONG));
        // RV-F: void yields no output to describe; the flag says so.
        assertThat(reviewed.output()).isEqualTo(Primitive.STRING);
        assertThat(reviewed.annotations()).contains(Annotation.flag(DraftIntake.VOID_ANNOTATION));
        assertThat(reviewed.review().label()).isEqualTo(ReviewLabel.UNKNOWN);
        assertValid(report);
    }

    // ---------- RV-4: pending is a queue, rejected is silence ----------

    @Test
    void rv4_pendingIsCountedAndRejectedProducesNothing(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftReport drafted = provider.draft(new ProviderInput(root));
        List<DraftEntry> entries = new ArrayList<>(drafted.drafts());
        int summarize = indexOf(entries, "InvoiceServiceImpl_summarize");
        entries.set(summarize, edit(entries.get(summarize)).review(DraftEntry.REJECTED).build());
        writeDrafts(root, entries);

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.draftsPending()).isEqualTo(entries.size() - 1);
        assertThat(report.reviewedCount()).isZero();
        assertThat(report.draftRefusals()).isEmpty();
        assertThat(report.model().capabilities()).extracting(Capability::name).containsExactly("getInvoice");
    }

    // ---------- RV-5 and RV-6: the fingerprint pin and the label guard ----------

    @Test
    void rv5_aStampThatNoLongerMatchesRefusesStaleDraft(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, edit(approved(summarize, "Summarizes an invoice.")).fingerprint("sha256:0000").build());

        ScanReport report = provider.scan(new ProviderInput(root));

        DraftRefusal refusal = onlyRefusal(report, Cause.STALE_DRAFT);
        assertThat(refusal.target()).isEqualTo(SVC + "summarize(Long, String)");
        assertThat(refusal.detail()).contains(summarize.fingerprint()).contains("sha256:0000");
        assertThat(report.reviewedCount()).isZero();
    }

    @Test
    void rv5_aMissingStampRefusesStaleDraftToo(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, edit(approved(summarize, "Summarizes an invoice.")).fingerprint(null).build());

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(onlyRefusal(report, Cause.STALE_DRAFT).detail()).contains("no fingerprint");
    }

    @Test
    void rv6_aReadWhoseHelperGainedAWriteRefusesLabelDriftNamingBothLabels(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        DraftEntry search = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_search");
        assertThat(search.label()).isEqualTo(DraftLabel.READ);
        writeDrafts(root, approved(search, "Finds invoices whose number contains the fragment."));

        // The one-hop helper is in the labeled slice (read-safety follows it) but outside the fingerprinted
        // slice (the target, its imports, its repository fields and their interfaces, its return type), so
        // the stamp still matches and the drift is caught by RV-C, exactly the guard it exists for.
        rewriteFixture(root, "        return fragment.trim();",
                "        repo.save(new Invoice());\n        return fragment.trim();");

        ScanReport report = provider.scan(new ProviderInput(root));

        DraftRefusal refusal = onlyRefusal(report, Cause.LABEL_DRIFT);
        assertThat(refusal.detail()).contains("recorded label is READ").contains("now labels the target WRITE");
        assertThat(provider.attestationFingerprint(new ProviderInput(root), search.target()))
                .contains(search.fingerprint());
        assertThat(report.reviewedCount()).isZero();
    }

    @Test
    void rv6_aWriteAddedToTheBodyItselfIsCaughtByTheStampFirst(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry search = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_search");
        writeDrafts(root, approved(search, "Finds invoices whose number contains the fragment."));
        rewriteFixture(root, "        return repo.findByNumberContaining(normalize(fragment));",
                "        repo.save(new Invoice());\n        return repo.findByNumberContaining(normalize(fragment));");

        ScanReport report = provider.scan(new ProviderInput(root));

        // RV-B precedes RV-C by design: a changed slice is stale before it is anything else.
        onlyRefusal(report, Cause.STALE_DRAFT);
    }

    // ---------- RV-7: the proven tier wins ----------

    @Test
    void rv7_anApprovedEntryOnADiscoveredBindingRefusesSupersededAndLeavesTheVerifiedReadUnchanged(
            @TempDir Path root) throws IOException {
        copyFixture(root);
        ScanReport before = provider.scan(new ProviderInput(root));
        DraftEntry getById = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_getById");
        assertThat(getById.label()).isEqualTo(DraftLabel.VERIFIED);
        writeDrafts(root, approved(getById, "Reads an invoice by id."));

        ScanReport report = provider.scan(new ProviderInput(root));

        DraftRefusal refusal = onlyRefusal(report, Cause.SUPERSEDED);
        assertThat(refusal.detail()).contains("InvoiceServiceImpl#getById").contains("'getInvoice'");
        assertThat(report.model()).isEqualTo(before.model());
        assertThat(report.reviewedCount()).isZero();
    }

    @Test
    void rv7_twoApprovedEntriesOnOneTargetAdmitTheFirstAndRefuseTheSecond(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, List.of(approved(summarize, "Summarizes an invoice."),
                edit(approved(summarize, "Summarizes an invoice, again.")).name("again").build()));

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.reviewedCount()).isEqualTo(1);
        assertThat(onlyRefusal(report, Cause.SUPERSEDED).name()).isEqualTo("again");
        assertValid(report);
    }

    // ---------- RV-8: every input binds, or the entry names the one that does not ----------

    @Test
    void rv8_aCollectionInputBindsAsMultipleSinceDl2a(@TempDir Path root) throws IOException {
        // Before DL-2a this refused INPUT_UNMAPPED by name (decision D6); since DL-2a (section 4.19.2, R1) a
        // List of a primitive binds as its element carried as an array, and the approval covers it.
        copyFixture(root);
        DraftEntry byIds = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_byIds");
        assertThat(byIds.inputs().get(0).carriedAsArray()).isTrue();
        writeDrafts(root, approved(byIds, "Reads the invoices with the given ids."));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability reviewed = capability(report, "reviewed_InvoiceServiceImpl_byIds");
        assertThat(reviewed.inputs()).containsExactly(Map.entry("ids", Primitive.LONG));
        assertThat(reviewed.multipleInputs()).containsExactly("ids");
        assertThat(report.draftRefusals()).isEmpty();
        assertValid(report);
    }

    @Test
    void rv8_anInTreeEnumInputBindsWithItsClosedSetAndTheEntityListOutputTies(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        DraftReport drafted = provider.draft(new ProviderInput(root));
        writeDrafts(root, List.of(
                approved(entry(drafted, "InvoiceServiceImpl_byStatus"), "Reads the invoices in a status."),
                approved(entry(drafted, "InvoiceServiceImpl_countByStatus"), "Counts the invoices in a status.")));

        ScanReport report = provider.scan(new ProviderInput(root));

        Capability byStatus = capability(report, "reviewed_InvoiceServiceImpl_byStatus");
        assertThat(byStatus.inputs()).containsExactly(Map.entry("status", Primitive.STRING));
        assertThat(byStatus.inputValues()).containsExactly(Map.entry("status", List.of("OPEN", "PAID", "VOID")));
        assertThat(byStatus.output()).isEqualTo(Type.entityRef("Invoice"));
        assertThat(byStatus.outputMultiple()).isTrue();
        assertThat(byStatus.annotations()).extracting(Annotation::key).containsExactly("description");

        Capability count = capability(report, "reviewed_InvoiceServiceImpl_countByStatus");
        assertThat(count.output()).isEqualTo(Primitive.LONG);
        assertThat(count.outputMultiple()).isFalse();
        assertThat(report.draftRefusals()).isEmpty();
        assertValid(report);
    }

    // ---------- RV-9: the human half ----------

    @Test
    void rv9_aMissingOrMalformedHumanHalfRefusesReviewIncomplete(@TempDir Path root) throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        DraftEntry ok = approved(summarize, "Summarizes an invoice.");
        List<DraftEntry> entries = List.of(
                edit(ok).name("blankDescription").description("").build(),
                edit(ok).name("blankReviewer").reviewedBy(" ").build(),
                edit(ok).name("blankDate").reviewedOn(null).build(),
                edit(ok).name("badOrigin").descriptionOrigin("robot").build(),
                edit(ok).name("badReview").review("aproved").build(),
                edit(ok).name("reviewed_prefixed").build(),
                edit(ok).name("not a name").build());
        writeDrafts(root, entries);

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(report.draftRefusals()).hasSize(entries.size());
        assertThat(report.draftRefusals()).allSatisfy(r -> assertThat(r.cause()).isEqualTo(Cause.REVIEW_INCOMPLETE));
        assertThat(report.draftRefusals()).extracting(DraftRefusal::name).containsExactly("blankDescription",
                "blankReviewer", "blankDate", "badOrigin", "badReview", "reviewed_prefixed", "not a name");
        assertThat(report.reviewedCount()).isZero();
        assertThat(report.draftsPending()).isZero();
    }

    @Test
    void anAiDraftedDescriptionShipsOnlyUnderANamedApprovalAndKeepsItsOrigin(@TempDir Path root)
            throws IOException {
        copyFixture(root);
        DraftEntry summarize = entry(provider.draft(new ProviderInput(root)), "InvoiceServiceImpl_summarize");
        writeDrafts(root, edit(approved(summarize, "Summarizes an invoice."))
                .descriptionOrigin(DraftEntry.ORIGIN_AI_DRAFT).build());

        ScanReport report = provider.scan(new ProviderInput(root));

        assertThat(capability(report, "reviewed_InvoiceServiceImpl_summarize").review().descriptionOrigin())
                .isEqualTo("ai-draft");
        assertValid(report);
    }

    @Test
    void aMalformedDraftsFileIsOneRefusalAndTheModelIsUntouched(@TempDir Path root) throws IOException {
        copyFixture(root);
        ScanReport before = provider.scan(new ProviderInput(root));
        Files.createDirectories(root.resolve("tibyan"));
        Files.writeString(root.resolve(DraftsFile.RELATIVE_PATH), "{ this is not json");

        ScanReport report = provider.scan(new ProviderInput(root));

        DraftRefusal refusal = onlyRefusal(report, Cause.FILE_ERROR);
        assertThat(refusal.target()).isEqualTo("(" + DraftsFile.RELATIVE_PATH + ")");
        assertThat(report.model()).isEqualTo(before.model());
    }

    // ---------- RV-11: the merge ----------

    @Test
    void rv11_reRunningDraftMergesAndNeverOverwrites(@TempDir Path root) throws IOException {
        copyFixture(root);
        ProviderInput input = new ProviderInput(root);
        DraftReport first = provider.draft(input);
        List<DraftEntry> reviewed = new ArrayList<>(first.drafts());
        int summarize = indexOf(reviewed, "InvoiceServiceImpl_summarize");
        reviewed.set(summarize, edit(approved(reviewed.get(summarize), "Summarizes an invoice."))
                .name("invoiceSummary").descriptionOrigin(DraftEntry.ORIGIN_AI_DRAFT).build());
        int search = indexOf(reviewed, "InvoiceServiceImpl_search");
        reviewed.set(search, approved(reviewed.get(search), "Finds invoices by number fragment."));
        int byIds = indexOf(reviewed, "InvoiceServiceImpl_byIds");
        String byIdsFingerprint = reviewed.get(byIds).fingerprint();
        Path file = DraftsFile.defaultLocation(root);
        DraftsFile.write(file, reviewed);

        // The code moves: byIds vanishes, total appears, and search's helper gains a write (READ to WRITE).
        rewriteFixture(root, "    public List<Invoice> byIds(List<Long> ids) {\n        return repo.findByIdIn(ids);\n    }\n",
                "    public long total() {\n        return repo.findAll().size();\n    }\n");
        rewriteFixture(root, "        return fragment.trim();",
                "        repo.save(new Invoice());\n        return fragment.trim();");

        DraftReport second = provider.draft(input, DraftsFile.read(file));
        List<DraftEntry> merged = second.drafts();

        // The approved entry keeps every reviewer field and takes the tool's fresh facts and stamp.
        DraftEntry kept = byName(merged, "invoiceSummary");
        assertThat(kept.target()).isEqualTo(SVC + "summarize(Long, String)");
        assertThat(kept.description()).isEqualTo("Summarizes an invoice.");
        assertThat(kept.descriptionOrigin()).isEqualTo("ai-draft");
        assertThat(kept.review()).isEqualTo(DraftEntry.APPROVED);
        assertThat(kept.reviewedBy()).isEqualTo(REVIEWER);
        assertThat(kept.reviewedOn()).isEqualTo(DATE);
        assertThat(kept.fingerprint()).isEqualTo(byTarget(first, SVC + "summarize(Long, String)").fingerprint());
        assertThat(kept.stale()).isFalse();
        assertThat(kept.previousLabel()).isNull();

        // The label-changed entry is reset to pending with both labels, the reviewer fields kept as history.
        DraftEntry drifted = byName(merged, "InvoiceServiceImpl_search");
        assertThat(drifted.label()).isEqualTo(DraftLabel.WRITE);
        assertThat(drifted.previousLabel()).isEqualTo(DraftLabel.READ);
        assertThat(drifted.review()).isEqualTo(DraftEntry.PENDING);
        assertThat(drifted.description()).isEqualTo("Finds invoices by number fragment.");
        assertThat(drifted.reviewedBy()).isEqualTo(REVIEWER);

        // The vanished target is kept in place, stale, with its old stamp; the new method is appended pending.
        DraftEntry stale = byName(merged, "InvoiceServiceImpl_byIds");
        assertThat(stale.stale()).isTrue();
        assertThat(stale.fingerprint()).isEqualTo(byIdsFingerprint);
        assertThat(stale.review()).isEqualTo(DraftEntry.PENDING);
        DraftEntry added = byName(merged, "InvoiceServiceImpl_total");
        assertThat(added.review()).isEqualTo(DraftEntry.PENDING);
        assertThat(added.label()).isEqualTo(DraftLabel.READ);
        assertThat(added.stale()).isFalse();

        // Order: live candidates in extractor order (alphabetical by method), then stale ones last.
        assertThat(merged).extracting(DraftEntry::name).containsExactly(
                "InvoiceServiceImpl_byStatus", "InvoiceServiceImpl_countByStatus", "InvoiceServiceImpl_getById",
                "InvoiceServiceImpl_open", "InvoiceServiceImpl_reindex", "InvoiceServiceImpl_search",
                "invoiceSummary", "InvoiceServiceImpl_total", "InvoiceServiceImpl_byIds");
        assertThat(merged).extracting(DraftEntry::name).doesNotHaveDuplicates();
        assertThat(second.counts()).containsEntry("WRITE", 2).containsEntry("VERIFIED", 1);

        // The merged file round-trips, and the stale flag and both labels are written.
        DraftsFile.write(file, second);
        String rendered = Files.readString(file);
        assertThat(rendered).contains("\"stale\" : true").contains("\"previousLabel\" : \"READ\"")
                .contains("\"reviewedBy\" : \"a.reviewer\"");
        assertThat(DraftsFile.read(file)).isEqualTo(merged);
    }

    @Test
    void rv11_aNewCandidateNeverTakesAReviewersName() {
        DraftEntry stale = DraftEntry.fresh("Svc_find", "a.Svc#find(Long)", "Svc#find", DraftLabel.READ, "r", null,
                "X", List.of(), "sha256:old");
        DraftEntry fresh = DraftEntry.fresh("Svc_find", "a.Svc#find(String)", "Svc#find", DraftLabel.READ, "r", null,
                "X", List.of(), "sha256:new");

        List<DraftEntry> merged = DraftMerge.merge(List.of(fresh), List.of(stale));

        assertThat(merged).extracting(DraftEntry::name).containsExactly("Svc_find_2", "Svc_find");
        assertThat(merged.get(0).stale()).isFalse();
        assertThat(merged.get(1).stale()).isTrue();
    }

    @Test
    void rv11_aHandSpelledTargetWithoutSpacesStillMatches() {
        DraftEntry existing = new DraftEntry("mine", "a.Svc#find(Long,String)", "Svc#find", DraftLabel.READ, "r",
                null, "X", List.of(), "words", DraftEntry.APPROVED, REVIEWER, DATE, "human", "sha256:old", false,
                null);
        DraftEntry fresh = DraftEntry.fresh("Svc_find", "a.Svc#find(Long, String)", "Svc#find", DraftLabel.READ,
                "r", null, "X", List.of(), "sha256:new");

        List<DraftEntry> merged = DraftMerge.merge(List.of(fresh), List.of(existing));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).name()).isEqualTo("mine");
        assertThat(merged.get(0).review()).isEqualTo(DraftEntry.APPROVED);
        // An approved entry keeps the fingerprint the reviewer approved (build decision, section 4.17.13).
        assertThat(merged.get(0).fingerprint()).isEqualTo("sha256:old");
    }

    // ---------- BI-10 (Binding Identity, Volume 7 section 12.6): a drafts file re-qualifies on merge ----------

    @Test
    void bi10_anExistingApprovedEntryWithAnUnqualifiedBindingReQualifiesAndKeepsItsReview() {
        // An adopter's drafts file written before Binding Identity carries Svc#find; the next run's fresh
        // candidate carries a.Svc#find. carry() takes the fresh binding and every reviewer field of the old
        // entry, including the fingerprint the reviewer approved.
        DraftEntry existing = new DraftEntry("mine", "a.Svc#find(Long)", "Svc#find", DraftLabel.READ, "r",
                null, "X", List.of(), "words", DraftEntry.APPROVED, REVIEWER, DATE, "human", "sha256:old", false,
                null);
        DraftEntry fresh = DraftEntry.fresh("Svc_find", "a.Svc#find(Long)", "a.Svc#find", DraftLabel.READ,
                "r", null, "X", List.of(), "sha256:old");

        List<DraftEntry> merged = DraftMerge.merge(List.of(fresh), List.of(existing));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).binding()).isEqualTo("a.Svc#find");
        assertThat(merged.get(0).name()).isEqualTo("mine");
        assertThat(merged.get(0).review()).isEqualTo(DraftEntry.APPROVED);
        assertThat(merged.get(0).reviewedBy()).isEqualTo(REVIEWER);
        assertThat(merged.get(0).description()).isEqualTo("words");
        assertThat(merged.get(0).fingerprint()).isEqualTo("sha256:old");
        assertThat(merged.get(0).stale()).isFalse();
    }

    // ---------- RV-12: byte identity without an approved draft ----------

    @Test
    void rv12_aScanWithNoApprovedDraftSerializesByteIdentically(@TempDir Path root) throws IOException {
        copyFixture(root);
        ProviderInput input = new ProviderInput(root);
        ScanReport bare = provider.scan(input);
        assertThat(bare.model().capabilities()).noneMatch(Capability::hasReview);
        assertThat(bare.model().capabilities()).noneMatch(c -> c.name().startsWith("reviewed_"));

        DraftReport drafted = provider.draft(input);
        List<DraftEntry> entries = new ArrayList<>(drafted.drafts());
        int summarize = indexOf(entries, "InvoiceServiceImpl_summarize");
        entries.set(summarize, edit(entries.get(summarize)).review(DraftEntry.REJECTED).build());
        writeDrafts(root, entries);

        ScanReport withFile = provider.scan(input);
        assertThat(withFile.model()).isEqualTo(bare.model());
        assertThat(withFile.entityOutcomes()).isEqualTo(bare.entityOutcomes());
        assertThat(withFile.draftsPending()).isEqualTo(entries.size() - 1);
        assertThat(withFile.draftRefusals()).isEmpty();
    }

    // ---------- helpers ----------

    private void assertValid(ScanReport report) {
        ValidationResult result = validator.validate(report.model());
        assertThat(result.isValid()).as("validation errors: %s", result.errors()).isTrue();
    }

    private static DraftRefusal onlyRefusal(ScanReport report, Cause cause) {
        assertThat(report.draftRefusals()).as("refusals").hasSize(1);
        DraftRefusal refusal = report.draftRefusals().get(0);
        assertThat(refusal.cause()).as(refusal.detail()).isEqualTo(cause);
        return refusal;
    }

    private static Capability capability(ScanReport report, String name) {
        return report.model().capabilities().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name + "; capabilities were "
                        + report.model().capabilities().stream().map(Capability::name).toList()
                        + "; draft refusals were " + report.draftRefusals()));
    }

    private static DraftEntry entry(DraftReport report, String name) {
        return byName(report.drafts(), name);
    }

    private static DraftEntry byName(List<DraftEntry> entries, String name) {
        Optional<DraftEntry> entry = entries.stream().filter(d -> d.name().equals(name)).findFirst();
        assertThat(entry).as("draft named " + name + " among "
                + entries.stream().map(DraftEntry::name).toList()).isPresent();
        return entry.get();
    }

    private static DraftEntry byTarget(DraftReport report, String target) {
        Optional<DraftEntry> entry = report.drafts().stream().filter(d -> d.target().equals(target)).findFirst();
        assertThat(entry).as("draft targeting " + target).isPresent();
        return entry.get();
    }

    private static int indexOf(List<DraftEntry> entries, String name) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("no draft named " + name);
    }

    private static DraftEntry approved(DraftEntry entry, String description) {
        return edit(entry).description(description).review(DraftEntry.APPROVED)
                .reviewedBy(REVIEWER).reviewedOn(DATE).build();
    }

    private static Edit edit(DraftEntry entry) {
        return new Edit(entry);
    }

    private static void writeDrafts(Path root, DraftEntry entry) {
        writeDrafts(root, List.of(entry));
    }

    private static void writeDrafts(Path root, List<DraftEntry> entries) {
        DraftsFile.write(DraftsFile.defaultLocation(root), entries);
    }

    private static void copyFixture(Path root) throws IOException {
        Files.write(root.resolve("DraftReview.java"), Files.readAllBytes(FIXTURE));
    }

    private static void rewriteFixture(Path root, String from, String to) throws IOException {
        Path copy = root.resolve("DraftReview.java");
        String source = Files.readString(copy, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(source).as("fixture text to rewrite").contains(from);
        Files.writeString(copy, source.replace(from, to), StandardCharsets.UTF_8);
    }

    /** A reviewer's edit of one entry: every field starts as the entry's and any may be overridden. */
    private static final class Edit {
        private String name;
        private final String target;
        private final String binding;
        private final DraftLabel label;
        private final String reason;
        private final String capability;
        private final String returnType;
        private final List<com.tibyaan.provider.spring.draft.DraftInput> inputs;
        private String description;
        private String review;
        private String reviewedBy;
        private String reviewedOn;
        private String descriptionOrigin;
        private String fingerprint;
        private final boolean stale;
        private final DraftLabel previousLabel;

        Edit(DraftEntry e) {
            name = e.name();
            target = e.target();
            binding = e.binding();
            label = e.label();
            reason = e.reason();
            capability = e.capability();
            returnType = e.returnType();
            inputs = e.inputs();
            description = e.description();
            review = e.review();
            reviewedBy = e.reviewedBy();
            reviewedOn = e.reviewedOn();
            descriptionOrigin = e.descriptionOrigin();
            fingerprint = e.fingerprint();
            stale = e.stale();
            previousLabel = e.previousLabel();
        }

        Edit name(String value) {
            name = value;
            return this;
        }

        Edit description(String value) {
            description = value;
            return this;
        }

        Edit review(String value) {
            review = value;
            return this;
        }

        Edit reviewedBy(String value) {
            reviewedBy = value;
            return this;
        }

        Edit reviewedOn(String value) {
            reviewedOn = value;
            return this;
        }

        Edit descriptionOrigin(String value) {
            descriptionOrigin = value;
            return this;
        }

        Edit fingerprint(String value) {
            fingerprint = value;
            return this;
        }

        DraftEntry build() {
            return new DraftEntry(name, target, binding, label, reason, capability, returnType, inputs, description,
                    review, reviewedBy, reviewedOn, descriptionOrigin, fingerprint, stale, previousLabel);
        }
    }
}
