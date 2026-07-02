package com.example.banking;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled): an entity whose backing finder is deliberately NOT named
 * findById, on a service deliberately NOT named AccountService. If the provider re-encoded the
 * old get<Entity> -> <Entity>Service#findById convention, it could not produce this binding.
 */
@Entity
public class Account {

    @Id
    private Long id;

    private String owner;
}
