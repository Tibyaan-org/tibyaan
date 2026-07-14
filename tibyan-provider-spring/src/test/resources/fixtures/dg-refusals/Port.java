package example.refuse;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), DG-5. The entity behind a port with more than one implementation. */
@Entity
public class Port {

    @Id
    private Long id;

    private String label;
}
