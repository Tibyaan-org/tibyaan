package com.example.resilient;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled): the read-by-id finder that backs {@code Widget}, so a resilient scan
 * discovers {@code getWidget} even though a sibling file in the tree cannot be parsed.
 */
@Service
public class WidgetService {

    public Widget findById(Long id) {
        return new Widget();
    }
}
