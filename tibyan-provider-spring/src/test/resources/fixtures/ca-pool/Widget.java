package ca.pool;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class Widget {
    @Id
    Long id;
}

interface WidgetJpaRepo extends JpaRepository<Widget, Long> {
}

@Service
class WidgetService {

    private final WidgetJpaRepo repo;
    public Widget findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class WidgetController {
    private final WidgetService widgetService;

    WidgetController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WIDGET_READ')")
    public Widget getWidget(@PathVariable Long id) {
        return widgetService.findById(id);
    }
}
