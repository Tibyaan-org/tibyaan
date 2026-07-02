package com.example.floats;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): C1, a Double field maps to FLOAT. */
@Entity
public class Measure {

    @Id
    private Long id;

    private Double amount;
}
