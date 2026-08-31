package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

// CA-10: hasAuthority(AUTH_READ) is a SpEL variable, not a string literal, even though an in-tree
// constant AUTH_READ exists. The tool never resolves a name inside SpEL: unverifiable, unparseable.
class Authorities {
    public static final String AUTH_READ = "AUTHREAD";
}

@Entity
class Trap {
    @Id
    Long id;
}

interface TrapJpaRepo extends JpaRepository<Trap, Long> {
}

@Service
class TrapService {

    private final TrapJpaRepo repo;
    public Trap findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class TrapController {
    private final TrapService trapService;

    TrapController(TrapService trapService) {
        this.trapService = trapService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(AUTH_READ)")
    public Trap getTrap(@PathVariable Long id) {
        return trapService.findById(id);
    }
}
