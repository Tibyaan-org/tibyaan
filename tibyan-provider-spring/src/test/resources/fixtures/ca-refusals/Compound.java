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
class Compound {
    @Id
    Long id;
}

interface CompoundJpaRepo extends JpaRepository<Compound, Long> {
}

@Service
class CompoundService {

    private final CompoundJpaRepo repo;
    public Compound findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class CompoundController {
    private final CompoundService service;

    CompoundController(CompoundService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('A') and hasAuthority('B')")
    public Compound getCompound(@PathVariable Long id) {
        return service.findById(id);
    }
}
