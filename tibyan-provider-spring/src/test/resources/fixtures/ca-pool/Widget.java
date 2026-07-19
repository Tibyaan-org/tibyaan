package ca.pool;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Widget {
    @Id
    Long id;
}

@Service
class WidgetService {
    public Widget findById(Long id) {
        return null;
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
