package example.fleet;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-10. The child's own read. It must bind HERE, never to the parent's
 * or the sibling's method, even though all three take the same inherited id type. */
interface CarJpaRepo extends JpaRepository<Car, Long> {
}

@Service
public class CarService {

    private final CarJpaRepo repo;

    public Car getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
