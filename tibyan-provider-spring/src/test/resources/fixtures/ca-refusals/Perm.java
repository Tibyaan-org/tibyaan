package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class Perm {
    @Id
    Long id;
}

interface PermJpaRepo extends JpaRepository<Perm, Long> {
}

@Service
class PermService {

    private final PermJpaRepo repo;
    public Perm findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class PermController {
    private final PermService service;

    PermController(PermService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Thing', 'read')")
    public Perm getPerm(@PathVariable Long id) {
        return service.findById(id);
    }
}
