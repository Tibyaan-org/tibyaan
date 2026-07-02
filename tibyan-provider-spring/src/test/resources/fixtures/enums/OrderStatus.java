package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): B5, the name trap. Despite the {@code Status} name, this is an
 * @Entity, not an enum, so a field of this type must route to the association rule.
 */
@Entity
public class OrderStatus {

    @Id
    private Long id;

    private String label;
}
