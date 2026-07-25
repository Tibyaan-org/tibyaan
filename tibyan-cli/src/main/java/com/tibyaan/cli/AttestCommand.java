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

    @Override
    public Integer call() {
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
}
