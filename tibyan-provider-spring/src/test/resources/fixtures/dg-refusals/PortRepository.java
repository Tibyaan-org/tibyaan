package example.refuse;

import java.util.Optional;

/** Test fixture (not compiled), DG-5. A hand-written repository port. */
public interface PortRepository {

    Optional<Port> selectById(Long id);
}
