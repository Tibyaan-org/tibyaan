package example.helper;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), DG-3b. The entity an entity-returning helper hop reads. */
@Entity
public class Bar {

    @Id
    private Long id;

    private String code;
}
