package com.example.resilient;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): a clean entity that sits in the same source tree as an unparseable
 * file, to prove collect-and-continue still discovers it (Volume 5 section 4, phase E1).
 */
@Entity
public class Widget {

    @Id
    private Long id;

    private String label;
}
