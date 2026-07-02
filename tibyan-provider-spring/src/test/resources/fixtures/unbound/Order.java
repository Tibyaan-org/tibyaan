package com.example.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): an entity whose service has no method of the read-by-id shape, so
 * the binding cannot be discovered and the provider must fail loudly (Volume 5 section 4.1).
 */
@Entity
public class Order {

    @Id
    private Long id;

    private String reference;
}
