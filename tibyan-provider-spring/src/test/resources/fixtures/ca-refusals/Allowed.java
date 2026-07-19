package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.security.RolesAllowed;

@Entity
class Allowed {
    @Id
    Long id;
}

@Service
class AllowedService {
    public Allowed findById(Long id) {
        return null;
    }
}

@RestController
class AllowedController {
    private final AllowedService service;

    AllowedController(AllowedService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @RolesAllowed("MANAGER")
    public Allowed getAllowed(@PathVariable Long id) {
        return service.findById(id);
    }
}
