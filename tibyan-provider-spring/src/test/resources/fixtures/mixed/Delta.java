package com.example.mixed;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): an entity whose service returns a view type, not the entity, so no
 * method has the read-by-id shape and the binding cannot be discovered (trial cause d, and a
 * preview of the DTO-return gap that phase E2 will close).
 */
@Entity
public class Delta {

    @Id
    private Long id;

    private String note;
}
