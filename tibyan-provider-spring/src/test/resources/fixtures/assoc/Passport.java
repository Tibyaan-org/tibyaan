package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

/** Test fixture (not compiled): A2, a @OneToOne maps with kind ONE_TO_ONE. */
@Entity
public class Passport {

    @Id
    private Long id;

    @OneToOne
    private Person holder;
}
