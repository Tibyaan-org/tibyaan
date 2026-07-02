package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): an association target entity. */
@Entity
public class Company {

    @Id
    private Long id;

    private String name;
}
