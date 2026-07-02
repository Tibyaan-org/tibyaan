package com.example.mixed;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): an entity with an association field whose type is another entity,
 * which has no equivalent in the closed primitive set, so extraction fails loudly (trial cause f3,
 * associations, out of scope until phase E3).
 */
@Entity
public class Beta {

    @Id
    private Long id;

    private Alpha owner;
}
