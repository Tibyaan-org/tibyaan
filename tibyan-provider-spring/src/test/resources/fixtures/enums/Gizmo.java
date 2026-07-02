package com.example.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled): B3, a fully qualified enum type resolves by last segment. */
@Entity
public class Gizmo {

    @Id
    private Long id;

    private com.example.enums.Color shade;
}
