package com.tibyaan.cli;

import com.tibyaan.plugin.ProviderInput;
import com.tibyaan.provider.spring.ProviderException;
import com.tibyaan.provider.spring.SpringCapabilityProvider;
import com.tibyaan.provider.spring.SpringCapabilityProvider.StampOutcome;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code tibyan attest} (Volume 5 section 4.15.3): stamps the structural fingerprint (and, with
 * {@code --date}, the assertion date) into every resolvable entry of the adopter's committed
 * {@code tibyan/attestations.json}, over the current code, and writes it back. This is the only path
 * that stamps a fingerprint, so a fingerprint always co-arrives with the code it pins; a hand-authored
 * entry without one is refused at intake, never silently completed. The human authors the claim (the
 * meaning is theirs, never the tool's); the tool computes only the fingerprint. Attesting the meaning
 * itself remains a human act; the assist that could draft it is AT-4, unbuilt, and its drafts never ship.
 *
 * <p>Exit codes: 0 when the file was stamped (whatever resolved); 3 when the scan could not run.
 */
@Command(name = "attest",
        description = "Stamp the structural fingerprint into tibyan/attestations.json against the current code.")
final class AttestCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<path>",
            description = "Path to the system whose tibyan/attestations.json to stamp.")
    Path path;

    @Option(names = "--date", paramLabel = "<yyyy-mm-dd>",
            description = "The assertion date to stamp; when omitted, each entry's existing date is kept.")
    String date;

    // AT-4 (Volume 5 section 4.15.5). Confirmation takes ONE target and ONE confirmer, and there is no
    // --confirm-all and no --confirm-many, by construction rather than by a check: confirmation is a
    // per-entry human act, and an option that confirmed a queue would be the batch-confirm the rule text
    // forbids. A team with ten drafts reviews ten drafts.
    @Option(names = "--confirm", paramLabel = "<target>",
            description = "Confirm ONE drafted entry by its target, taking its claim on as your own. "
                    + "Requires --by. There is deliberately no confirm-all.")
    String confirm;

    @Option(names = "--by", paramLabel = "<name>",
            description = "The confirmer's name, recorded in assertedBy. Required with --confirm.")
    String by;

    @Override
    public Integer call() {
        if (confirm != null) {
            return confirmOne();
        }
        System.out.println("Stamping attestations under " + path);
        List<StampOutcome> outcomes;
        try {
            outcomes = new SpringCapabilityProvider().stampAttestations(new ProviderInput(path), date);
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        }
        if (outcomes.isEmpty()) {
            System.out.println("No attestations file found; nothing to stamp.");
            return 0;
        }
        long stamped = outcomes.stream().filter(StampOutcome::resolved).count();
        for (StampOutcome outcome : outcomes) {
            if (outcome.resolved()) {
                System.out.println("  stamped " + outcome.target() + " -> " + outcome.fingerprint());
            } else {
                System.out.println("  UNRESOLVED " + outcome.target()
                        + " (does not resolve to one in-tree method; not stamped)");
            }
        }
        System.out.println("Stamped " + stamped + " of " + outcomes.size()
                + " entr" + (outcomes.size() == 1 ? "y" : "ies") + "; run tibyan scan to intake them.");
        return 0;
    }

    // Confirming one draft: the human act the whole assist boundary turns on. It rewrites that entry's
    // origin to ai-drafted-human-confirmed and records the confirmer in assertedBy, and it rewrites no
    // words: confirming means a person read the proposal and takes it on as their own claim, and if they
    // want different words they edit the file, which is the same reviewable record either way.
    private Integer confirmOne() {
        SpringCapabilityProvider.ConfirmOutcome outcome;
        try {
            outcome = new SpringCapabilityProvider().confirmDraft(new ProviderInput(path), confirm, by);
        } catch (ProviderException e) {
            System.err.println("Provider error: " + e.getMessage());
            return 3;
        }
        if (!outcome.confirmed()) {
            System.err.println("Not confirmed: " + outcome.reason());
            return 1;
        }
        System.out.println("Confirmed " + confirm);
        System.out.println("  origin is now ai-drafted-human-confirmed, asserted by " + by + ".");
        System.out.println("  The claim is yours now, not the assist's. Run tibyan attest to stamp its "
                + "fingerprint, then tibyan scan to intake it.");
        return 0;
    }
}
