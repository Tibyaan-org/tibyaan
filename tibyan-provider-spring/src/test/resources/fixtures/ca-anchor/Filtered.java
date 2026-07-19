package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Filtered {
    @Id
    Long id;
}

@Service
class FilteredService {
    public Filtered findById(Long id) {
        return null;
    }

    public Filtered search(String q) {
        return null;
    }
}

@RestController
class FilteredController {
    private final FilteredService filteredService;

    FilteredController(FilteredService filteredService) {
        this.filteredService = filteredService;
    }

    @GetMapping("/{id}")
    public Filtered getFiltered(@PathVariable Long id) {
        return filteredService.findById(id);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SEARCH_AUTH')")
    public Filtered search(@RequestParam String q) {
        return filteredService.search(q);
    }
}
