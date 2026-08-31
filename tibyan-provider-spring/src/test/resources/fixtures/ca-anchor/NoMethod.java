package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class NoMethod {
    @Id
    Long id;
}

interface NoMethodJpaRepo extends JpaRepository<NoMethod, Long> {
}

@Service
class NoMethodService {

    private final NoMethodJpaRepo repo;
    public NoMethod findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class NoMethodController {
    private final NoMethodService noMethodService;

    NoMethodController(NoMethodService noMethodService) {
        this.noMethodService = noMethodService;
    }

    @RequestMapping("/nomethod/{id}")
    @PreAuthorize("hasAuthority('NM_READ')")
    public NoMethod getNoMethod(@PathVariable Long id) {
        return noMethodService.findById(id);
    }
}
