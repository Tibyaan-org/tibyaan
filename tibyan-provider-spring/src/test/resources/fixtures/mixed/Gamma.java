package com.example.mixed;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled): an entity whose fields all map but which has no {@code @Id}, so no
 * read-by-id is modeled (trial cause c, composite or non-@Id keys, out of scope).
 */
@Entity
public class Gamma {

    private String code;

    private String label;
}
