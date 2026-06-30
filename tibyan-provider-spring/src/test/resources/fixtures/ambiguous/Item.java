package com.example.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): an entity whose service has two methods of the read-by-id shape.
 * The provider must refuse to guess and fail loudly (Volume 5 section 4.1).
 */
@Entity
public class Item {

    @Id
    private Long id;

    private String label;
}
