package example.catalog;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-2. A clean entity-returning by-id read of the JOINED child. */
interface BundleItemJpaRepo extends JpaRepository<BundleItem, Long> {
}

@Service
public class BundleItemService {

    private final BundleItemJpaRepo repo;

    public BundleItem getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
