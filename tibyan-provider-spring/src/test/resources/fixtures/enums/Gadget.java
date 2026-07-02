package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): B2, a bare enum field with no @Enumerated maps identically. */
@Entity
public class Gadget {

    @Id
    private Long id;

    private Color color;
}
