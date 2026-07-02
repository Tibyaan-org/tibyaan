package com.example.mixed;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled): a service that returns a view type instead of the {@code Delta}
 * entity, so it does not have the read-by-id shape. The scan must record this as a located
 * NO_BACKING_METHOD failure, never guess a binding to it.
 */
@Service
public class DeltaService {

    public DeltaView findById(Long id) {
        return new DeltaView();
    }
}
