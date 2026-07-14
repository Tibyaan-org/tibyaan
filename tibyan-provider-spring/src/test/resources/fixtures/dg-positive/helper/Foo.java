package example.helper;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Test fixture (not compiled), DG-3. The entity a clean private-helper hop reads. */
@Entity
public class Foo {

    @Id
    private Long id;

    private String name;
}
