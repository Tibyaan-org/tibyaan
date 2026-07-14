package example.audit;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Test fixture (not compiled), DG-1. A standard-JPA @MappedSuperclass carrying the single @Id. */
@MappedSuperclass
public class AuditBase {

    @Id
    private Long id;

    private String createdBy;
}
