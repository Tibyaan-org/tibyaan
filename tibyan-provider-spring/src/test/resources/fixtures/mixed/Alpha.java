package com.example.mixed;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): the one entity a resilient scan should discover in this tree. */
@Entity
public class Alpha {

    @Id
    private Long id;

    private String name;
}
