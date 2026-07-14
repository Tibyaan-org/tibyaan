package example.crm;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), DG-11. The crm context's Note. */
@Entity
public class Note {

    @Id
    private Long id;

    private String subject;
}
