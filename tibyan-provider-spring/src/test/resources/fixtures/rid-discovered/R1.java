package rid.discovered.r1;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-1. The clean direct case: a Spring Data by-primary-key load
 * (condition 8, path 4a) returned through its unwrap chain and nothing else (condition 9a).
 * Discovers, exactly as it did before section 4.1.1, because the proof it now has to give is one it
 * could already give.
 */
@Entity
class Depot {
    @Id
    Long id;
    String code;
}

interface DepotJpaRepo extends JpaRepository<Depot, Long> {
}

@Service
class DepotService {

    private final DepotJpaRepo repo;

    public Depot getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
