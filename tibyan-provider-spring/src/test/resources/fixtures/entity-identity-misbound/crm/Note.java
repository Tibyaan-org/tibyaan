package example.crm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-6 (mis-bound guard). The crm Note has a backing finder; the
 * support Note (same simple name, different key) has none. The guard: the crm finder must back only
 * the crm Note, never be mis-bound to the support Note. Synthetic names.
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String subject;
}
