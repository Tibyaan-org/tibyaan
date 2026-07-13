package example.sales.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), EI-2. An Order in a second context, its simple name unique. */
@Entity
public class Order {

    @Id
    private Long id;

    private String reference;
}
