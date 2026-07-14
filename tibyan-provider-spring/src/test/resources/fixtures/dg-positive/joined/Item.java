package example.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

/** Test fixture (not compiled), DG-2. A standard-JPA @Inheritance(JOINED) parent carrying the @Id. */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Item {

    @Id
    private Long id;

    private String label;
}
