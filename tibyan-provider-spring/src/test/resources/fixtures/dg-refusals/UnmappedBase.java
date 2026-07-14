package example.refuse;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Test fixture (not compiled), DG-7c. The inherited @Id's type maps to no bindable primitive. */
@MappedSuperclass
public class UnmappedBase {

    @Id
    private CustomKey key;
}
