package ca.nogate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.annotation.Secured;
import org.springframework.data.jpa.repository.JpaRepository;

import static ca.nogate.Scopes.X_READ;

@Entity
class Legacy {
    @Id
    Long id;
}

interface LegacyJpaRepo extends JpaRepository<Legacy, Long> {
}

@Service
class LegacyService {

    private final LegacyJpaRepo repo;
    public Legacy findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class LegacyController {
    private final LegacyService legacyService;

    LegacyController(LegacyService legacyService) {
        this.legacyService = legacyService;
    }

    @GetMapping("/{id}")
    @Secured(X_READ)
    public Legacy getLegacy(@PathVariable Long id) {
        return legacyService.findById(id);
    }
}
