package ca.secured;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.annotation.Secured;
import org.springframework.data.jpa.repository.JpaRepository;

import static ca.secured.Scopes.BAD_CONST;

@Entity
class BadConst {
    @Id
    Long id;
}

interface BadConstJpaRepo extends JpaRepository<BadConst, Long> {
}

@Service
class BadConstService {

    private final BadConstJpaRepo repo;
    public BadConst findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class BadConstController {
    private final BadConstService badConstService;

    BadConstController(BadConstService badConstService) {
        this.badConstService = badConstService;
    }

    @GetMapping("/{id}")
    @Secured(BAD_CONST)
    public BadConst getBadConst(@PathVariable Long id) {
        return badConstService.findById(id);
    }
}
