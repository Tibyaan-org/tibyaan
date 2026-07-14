package example.refuse;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Test fixture (not compiled), DG-7b. The ROOT ancestor declares an @Id. */
@MappedSuperclass
public class RootBase {

    @Id
    private Long rootId;
}
