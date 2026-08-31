package com.example.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface ItemJpaRepository extends JpaRepository<Item, Long> {
}

/**
 * Test fixture (not compiled). Two methods share the read-by-id shape AND both prove their
 * by-primary-key load (section 4.1.1), so the backing method is ambiguous on the merits, not merely
 * on the signature. The provider must list both and fail, never arbitrarily pick one.
 */
@Service
public class ItemService {

    private final ItemJpaRepository repo;

    ItemService(ItemJpaRepository repo) {
        this.repo = repo;
    }

    public Item findById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    public Item loadById(Long id) {
        Item item = repo.findById(id).orElseThrow();
        return item;
    }
}
