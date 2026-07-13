package example.support.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test fixture (not compiled), EI-6. The support Note: a distinct class from the crm Note, with no
 * backing finder anywhere in the model. It must be reported NO_BACKING_METHOD, never bound to the
 * crm Note's finder. Synthetic names.
 */
@Entity
public class Note {

    @Id
    private Long id;

    private String ticketRef;
}
