package com.example.floats;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): C2, all four Java floating-point types map to FLOAT. */
@Entity
public class Readings {

    @Id
    private Long id;

    private double primitiveDouble;

    private float primitiveFloat;

    private Float boxedFloat;
}
