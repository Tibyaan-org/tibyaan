package example.support.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-1. A Note in the support bounded context: a distinct class from
 * the crm Note (a different key, different fields), sharing only the simple name. Synthetic names.
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String ticketRef;
}
