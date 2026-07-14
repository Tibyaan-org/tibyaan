package example.refuse;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled). An entity whose by-id read must stay REFUSED. */
@Entity
public class Widget {

    @Id
    private Long id;

    private String name;

    private Boolean isDeleted;

    public Boolean getIsDeleted() {
        return isDeleted;
    }
}
