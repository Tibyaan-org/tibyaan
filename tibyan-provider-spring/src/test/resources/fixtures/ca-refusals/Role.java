package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Role {
    @Id
    Long id;
}

@Service
class RoleService {
    public Role findById(Long id) {
        return null;
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
