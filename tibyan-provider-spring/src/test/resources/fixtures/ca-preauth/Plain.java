package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.jpa.repository.JpaRepository;

// CA-7: a clean anchored capability with no security annotation anywhere is ABSENT: it carries neither
// annotation and its manifest entry is byte-identical to the pre-phase entry.
@Entity
class Plain {
    @Id
    Long id;
}

interface PlainJpaRepo extends JpaRepository<Plain, Long> {
}

@Service
class PlainService {

    private final PlainJpaRepo repo;
    public Plain findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class PlainController {
    private final PlainService plainService;

    PlainController(PlainService plainService) {
        this.plainService = plainService;
    }

    @GetMapping("/{id}")
    public Plain getPlain(@PathVariable Long id) {
        return plainService.findById(id);
    }
}
