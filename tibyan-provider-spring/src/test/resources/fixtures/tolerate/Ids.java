package com.example.tolerate;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.Set;

/**
 * Test fixture (not compiled). T5 (latent id), T6 (the negative). Templated has an enum-typed @Id
 * and a blocking collection: once the collection is skipped it extracts and surfaces NO_IDENTIFIER,
 * where before the collection would have vetoed it. Nofind extracts partial but has no service, so
 * it still fails NO_BACKING_METHOD. Twinned has a composite key and still fails COMPOSITE_KEY even
 * though it extracts partial: tolerance never softened a discovery decision.
 */
enum Kind {
    ALPHA,
    BETA
}

@Entity
class Templated {
    @Id
    @Enumerated(EnumType.STRING)
    Kind kind;
    @OneToMany
    Set<Part> items;
    String name;
}

@Entity
class Nofind {
    @Id
    Long id;
    @OneToMany
    Set<Part> things;
    String name;
}

@Entity
class Twinned {
    @Id
    Long left;
    @Id
    Long right;
    @OneToMany
    Set<Part> things;
}
