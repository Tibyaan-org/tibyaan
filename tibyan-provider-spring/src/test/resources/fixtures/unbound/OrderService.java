package com.example.sales;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled). A real {@code @Service}, but no method has the read-by-id shape:
 * the finder returns the entity yet takes a {@code String}, not the {@code Long} id. The provider
 * must not fall back to any convention; it must fail with a clear, located error.
 */
@Service
public class OrderService {

    // Wrong parameter type: returns the entity but takes a String, not the Long id.
    public Order findByReference(String reference) {
        return null;
    }

    // Wrong shape entirely: no parameter and a non-entity return.
    public long count() {
        return 0;
    }
}
