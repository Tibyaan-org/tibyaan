package com.example.tolerate;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled). T2 (@Embeddable), T3 (byte[] binary), T4 (unresolvable library type):
 * each blocking field is skipped with its own located note and the entity extracts partial.
 */
@Embeddable
class Address {
    String street;
    String city;
}

@Entity
class Boxed {
    @Id
    Long id;
    @Embedded
    Address addr;
    String label;
}

@Entity
class Blob {
    @Id
    Long id;
    byte[] data;
    String name;
}

@Entity
class Libby {
    @Id
    Long id;
    com.acme.external.Money price;
    String name;
}
