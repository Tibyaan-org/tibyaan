package example.catalog;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-2. The parent's own read, so the parent is not left unbacked. */
interface ItemJpaRepo extends JpaRepository<Item, Long> {
}

@Service
public class ItemService {

    private final ItemJpaRepo repo;

    public Item getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
