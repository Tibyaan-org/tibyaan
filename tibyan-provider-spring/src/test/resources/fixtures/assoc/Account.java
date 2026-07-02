package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): A6, a field typed by an in-tree non-entity class refuses. */
@Entity
public class Account {

    @Id
    private Long id;

    private Money balance;
}
