package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Test fixture (not compiled): A1, a @ManyToOne to an in-tree entity. */
@Entity
public class Person {

    @Id
    private Long id;

    private String name;

    @ManyToOne
    private Company employer;
}
