package example.catalog;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-2. A JOINED child with no direct @Id: it inherits the parent's.
 */
@Entity
public class BundleItem extends Item {

    private Integer quantity;
}
