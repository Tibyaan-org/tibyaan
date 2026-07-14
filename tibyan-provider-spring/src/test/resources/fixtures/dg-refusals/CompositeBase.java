package example.refuse;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.MappedSuperclass;

/** Test fixture (not compiled), DG-7. A parent carrying a COMPOSITE key. */
@MappedSuperclass
public class CompositeBase {

    @EmbeddedId
    private Object key;
}
