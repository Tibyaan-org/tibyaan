package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Test fixture (not compiled): A9, an {@code @Id @ManyToOne} field maps as an ENTITY_REF field
 * (and contributes a Relation) while the read does not form, because the identifier is not a
 * bindable primitive. Composite-key read eligibility itself is section 4.2 (phase E2).
 */
@Entity
public class Enrollment {

    @Id
    @ManyToOne
    private Person student;
}
