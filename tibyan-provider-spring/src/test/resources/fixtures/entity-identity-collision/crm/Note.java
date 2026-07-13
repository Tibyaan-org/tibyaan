package example.crm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-1. A Note in the crm bounded context. A distinct class from the
 * support Note (a different key, different fields), sharing only the simple name. Synthetic names
 * (Volume 5 section 4.12; private-benchmark redaction).
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String subject;
}
