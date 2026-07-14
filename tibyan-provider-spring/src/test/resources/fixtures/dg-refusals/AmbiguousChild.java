package example.refuse;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-7b. Two different ancestors each declare an @Id. The tool will not
 * pick one of two competing identifiers: it REFUSES with a located ambiguous-hierarchy message,
 * distinct from a composite key, and never resolves to the nearer @Id.
 */
@Entity
public class AmbiguousChild extends MidBase {

    private String label;
}
