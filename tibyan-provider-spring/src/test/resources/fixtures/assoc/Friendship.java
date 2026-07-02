package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Test fixture (not compiled): A3, two fields to the same target dedupe to one Relation. */
@Entity
public class Friendship {

    @Id
    private Long id;

    @ManyToOne
    private Person a;

    @ManyToOne
    private Person b;
}
