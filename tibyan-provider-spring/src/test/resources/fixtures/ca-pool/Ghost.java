package ca.pool;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Ghost {
    @Id
    Long id;
    boolean deleted;
}

class GhostVTO {
}

interface GhostRepository extends JpaRepository<Ghost, Long> {
}

class GhostMapper {
    public GhostVTO toVto(Ghost g) {
        return null;
    }
}

@Service
class GhostService {
    private final GhostRepository ghostRepository;
    private final GhostMapper ghostMapper;

    GhostService(GhostRepository ghostRepository, GhostMapper ghostMapper) {
        this.ghostRepository = ghostRepository;
        this.ghostMapper = ghostMapper;
    }

    public GhostVTO findById(Long id) {
        Ghost g = ghostRepository.findById(id).orElseThrow();
        if (g.deleted) {
            throw new RuntimeException("deleted");
        }
        return ghostMapper.toVto(g);
    }
}

@RestController
class GhostController {
    private final GhostService ghostService;

    GhostController(GhostService ghostService) {
        this.ghostService = ghostService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GHOST_READ')")
    public GhostVTO getGhost(@PathVariable Long id) {
        return ghostService.findById(id);
    }
}
