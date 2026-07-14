package example.fleet;

import jakarta.persistence.Entity;

/** Test fixture (not compiled), DG-10. The SIBLING, which also inherits the parent's @Id. */
@Entity
public class Truck extends Vehicle {

    private Integer axles;
}
