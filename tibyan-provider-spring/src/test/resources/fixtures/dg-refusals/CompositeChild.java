package example.refuse;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-7. The child inherits an @EmbeddedId. A composite key is never read
 * by part of its key, so this reports COMPOSITE_KEY and forms no read intent.
 */
@Entity
public class CompositeChild extends CompositeBase {

    private String label;
}
