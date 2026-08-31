package example.fleet;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-10. The sibling's own read. */
interface TruckJpaRepo extends JpaRepository<Truck, Long> {
}

@Service
public class TruckService {

    private final TruckJpaRepo repo;

    public Truck getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
