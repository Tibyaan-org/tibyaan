package example.refuse;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Test fixture (not compiled), DG-7b. A SECOND ancestor also declares an @Id: the chain is ambiguous. */
@MappedSuperclass
public class MidBase extends RootBase {

    @Id
    private Long midId;
}
