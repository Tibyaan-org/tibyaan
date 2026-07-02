package com.example.floats;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/**
 * Test fixture (not compiled): C3, the exact-inexact line stays hard both ways. BigDecimal stays
 * DECIMAL and Double stays FLOAT; neither crosses.
 */
@Entity
public class Money2 {

    @Id
    private Long id;

    private BigDecimal exact;

    private Double inexact;
}
