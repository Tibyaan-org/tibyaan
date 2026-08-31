package example.fleet;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-10. The parent's own read. */
interface VehicleJpaRepo extends JpaRepository<Vehicle, Long> {
}

@Service
public class VehicleService {

    private final VehicleJpaRepo repo;

    public Vehicle getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
