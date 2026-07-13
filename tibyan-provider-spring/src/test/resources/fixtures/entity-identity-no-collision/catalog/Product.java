package example.catalog.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-2 (no regression). A multi-context model with no colliding simple
 * names: every entity must keep its simple name unchanged. Synthetic names.
 */
@Entity
public class Product {

    @Id
    private Long id;

    private String name;
}
