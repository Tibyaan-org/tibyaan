package example.fleet;

import jakarta.persistence.Entity;

/** Test fixture (not compiled), DG-10. A child inheriting the parent's @Id. */
@Entity
public class Car extends Vehicle {

    private Integer doors;
}
