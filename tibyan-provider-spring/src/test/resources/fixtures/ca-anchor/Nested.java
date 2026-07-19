package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Nested {
    @Id
    Long id;
}

@Service
class NestedService {
    public Nested findById(Long id) {
        return null;
    }

    public Nested map(Nested n) {
        return n;
    }
}

@RestController
class NestedController {
    private final NestedService nestedService;

    NestedController(NestedService nestedService) {
        this.nestedService = nestedService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NESTED_READ')")
    public Nested getNested(@PathVariable Long id) {
        return nestedService.map(nestedService.findById(id));
    }
}
