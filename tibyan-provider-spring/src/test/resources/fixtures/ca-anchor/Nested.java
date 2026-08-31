package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class Nested {
    @Id
    Long id;
}

interface NestedJpaRepo extends JpaRepository<Nested, Long> {
}

@Service
class NestedService {

    private final NestedJpaRepo repo;
    public Nested findById(Long id) {
        return repo.findById(id).orElseThrow();
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
