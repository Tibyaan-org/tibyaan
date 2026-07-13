package example.shared.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-15. The second distinct Note in the same package as moduleA's, in
 * a different source file. Same key, different class: a split-package clash, refused loud and never
 * merged. Synthetic names.
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String ticketRef;
}
