package example.audit;

import jakarta.persistence.Entity;

/**
 * Test fixture (not compiled), DG-1. A child entity that declares NO @Id: it inherits the single @Id
 * from the in-tree @MappedSuperclass one extends hop away. Before the phase this was NO_IDENTIFIER.
 */
@Entity
public class Note extends AuditBase {

    private String subject;
}
