package example.support;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), DG-11. The support context's Note: a DIFFERENT entity, same simple name. */
@Entity
public class Note {

    @Id
    private Long id;

    private String ticketRef;
}
