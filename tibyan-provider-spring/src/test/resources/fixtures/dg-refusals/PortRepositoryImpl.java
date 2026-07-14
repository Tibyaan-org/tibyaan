package example.refuse;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Test fixture (not compiled), DG-5. ONE implementation of the port. */
@Repository
public class PortRepositoryImpl implements PortRepository {

    private final PortJpaRepository jpaRepository;

    public PortRepositoryImpl(PortJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Port> selectById(Long id) {
        return jpaRepository.findById(id);
    }
}
