package com.example.modern;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): a plain entity whose service uses modern Java 17 syntax, so it only
 * parses when the provider's language level is Java 17 (Volume 5 section 4, phase E1).
 */
@Entity
public class Gadget {

    @Id
    private Long id;

    private String name;
}
