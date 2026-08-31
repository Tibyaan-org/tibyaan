package rid.discovered.r3;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-3. The private-helper hop on the ENTITY-returning path: the
 * public read's only indirection is one private same-class helper that performs the clean
 * by-primary-key load, and the public body returns that value untransformed.
 *
 * <p>This case is THE BUILD-FORCED CORRECTION of section 4.1.1. The ratified text first excluded the
 * helper hop from this path on the premise that no evidence presented the shape; fixture DG-3b
 * presents it, so excluding it would have regressed a read the Discovery-Gap phase already covered.
 * The trace is section 4.13.3's, unchanged, ending in the identity shape instead of a mapping step.
 */
@Entity
class Crate {
    @Id
    Long id;
    String code;
}

interface CrateJpaRepo extends JpaRepository<Crate, Long> {
}

@Service
class CrateService {

    private final CrateJpaRepo repo;

    public Crate getById(Long id) {
        return loadCrate(id);
    }

    private Crate loadCrate(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalStateException("not found"));
    }
}
