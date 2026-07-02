package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Test fixture (not compiled): B5, the name trap in use. {@code status} is typed by the
 * {@code OrderStatus} @Entity, so it maps as an ENTITY_REF association, never as an enum.
 */
@Entity
public class Order {

    @Id
    private Long id;

    @ManyToOne
    private OrderStatus status;
}
