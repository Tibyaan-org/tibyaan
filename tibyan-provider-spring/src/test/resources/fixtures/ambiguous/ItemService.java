package com.example.catalog;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled). Two methods share the read-by-id shape, so the backing method is
 * ambiguous. The provider must list both and fail, never arbitrarily pick one.
 */
@Service
public class ItemService {

    public Item findById(Long id) {
        return null;
    }

    public Item loadById(Long id) {
        return null;
    }
}
