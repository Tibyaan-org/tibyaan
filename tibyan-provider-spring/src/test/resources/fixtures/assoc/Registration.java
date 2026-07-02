package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Test fixture (not compiled): A7, a fully qualified entity type resolves by last segment. */
@Entity
public class Registration {

    @Id
    private Long id;

    @ManyToOne
    private com.example.assoc.Company org;
}
