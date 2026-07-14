package example.refuse;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-7c. The child inherits a single @Id whose type is not bindable. It
 * is refused exactly as a direct unmappable @Id is, with the refusal located at the declaring ancestor.
 */
@Entity
public class UnmappedChild extends UnmappedBase {

    private String label;
}
