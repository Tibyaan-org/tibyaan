package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): A4, an entity-typed field with no relationship annotation refuses. */
@Entity
public class Sponsorship {

    @Id
    private Long id;

    private Company sponsor;
}
