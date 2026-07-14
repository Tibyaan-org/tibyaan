package example.refuse;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Test fixture (not compiled), DG-5, the FOUNDATION-SPECIFIC NEGATIVE that must stay refused. A SECOND
 * in-tree implementation of the same port. The tool cannot know which one the container wires without
 * adopting a naming or wiring convention, so it must REFUSE. Resolving this would require a
 * foundation convention, which the design explicitly forbids: this is the boundary that must hold.
 */
@Repository
public class SecondPortRepositoryImpl implements PortRepository {

    @Override
    public Optional<Port> selectById(Long id) {
        return Optional.empty();
    }
}
