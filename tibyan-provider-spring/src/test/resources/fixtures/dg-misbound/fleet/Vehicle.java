package example.fleet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

/** Test fixture (not compiled), DG-10. The JOINED parent: it carries the @Id both children inherit. */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Vehicle {

    @Id
    private Long id;

    private String plate;
}
