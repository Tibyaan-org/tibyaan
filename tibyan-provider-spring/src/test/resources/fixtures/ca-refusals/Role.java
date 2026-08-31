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
class Role {
    @Id
    Long id;
}

interface RoleJpaRepo extends JpaRepository<Role, Long> {
}

@Service
class RoleService {

    private final RoleJpaRepo repo;
    public Role findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class RoleController {
    private final RoleService service;

    RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public Role getRole(@PathVariable Long id) {
        return service.findById(id);
    }
}
