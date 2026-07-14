package example.catalog;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-2. The parent's own read, so the parent is not left unbacked. */
@Service
public class ItemService {

    public Item getById(Long id) {
        return null;
    }
}
