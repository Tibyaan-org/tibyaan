package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;

/** Test fixture (not compiled): B1, an @Enumerated(STRING) enum field maps to STRING plus values. */
@Entity
public class Widget {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private Color color;
}
