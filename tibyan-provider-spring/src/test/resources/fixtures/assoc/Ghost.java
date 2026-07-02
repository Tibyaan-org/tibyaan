package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Test fixture (not compiled): A8, the name trap. The field type looks entity-ish but no
 * {@code Phantom} is declared in the tree, so resolution fails and the field refuses.
 */
@Entity
public class Ghost {

    @Id
    private Long id;

    @ManyToOne
    private Phantom ref;
}
