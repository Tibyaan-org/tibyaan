package example.refuse;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-6. The superclass is NOT in the scanned source (it would ship in a
 * binary dependency). The tool never assumes an unseen superclass carries an @Id: it must stay
 * NO_IDENTIFIER with a located note, never invent an inherited identifier.
 */
@Entity
public class Orphan extends ExternalBase {

    private String label;
}
