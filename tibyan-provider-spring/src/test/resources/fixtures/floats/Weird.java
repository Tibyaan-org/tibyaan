package com.example.floats;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): C5, an entity whose @Id is Double. The id field maps to FLOAT so
 * the entity extracts, but a FLOAT identifier forms no read intent (section 4.6, rule 4).
 */
@Entity
public class Weird {

    @Id
    private Double ratingId;

    private String label;
}
