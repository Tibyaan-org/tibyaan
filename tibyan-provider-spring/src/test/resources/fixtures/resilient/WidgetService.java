package com.example.resilient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface WidgetJpaRepository extends JpaRepository<Widget, Long> {
}

/**
 * Test fixture (not compiled): the read-by-id finder that backs {@code Widget}, so a resilient scan
 * discovers {@code getWidget} even though a sibling file in the tree cannot be parsed. The body is a
 * direct Spring Data by-primary-key load returned untransformed, which is what section 4.1.1
 * conditions 5 to 9 require of the entity-returning path.
 */
@Service
public class WidgetService {

    private final WidgetJpaRepository repo;

    WidgetService(WidgetJpaRepository repo) {
        this.repo = repo;
    }

    public Widget findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
