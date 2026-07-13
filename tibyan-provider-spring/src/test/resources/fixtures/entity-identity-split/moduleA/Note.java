package example.shared.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-15 (split-package clash). A distinct entity class sharing the
 * package and simple name of the one in moduleB, in a different source file: identical keys for
 * distinct classes. The provider must fail loud with the split-package message, never merge them.
 * Synthetic names.
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String subject;
}
