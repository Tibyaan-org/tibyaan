package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): B4, a field typed by an unresolvable type refuses. */
@Entity
public class Thing {

    @Id
    private Long id;

    private Mystery kind;
}
