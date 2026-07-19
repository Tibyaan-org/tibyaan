package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Perm {
    @Id
    Long id;
}

@Service
class PermService {
    public Perm findById(Long id) {
        return null;
    }
}

@RestController
class PermController {
    private final PermService service;

    PermController(PermService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Thing', 'read')")
    public Perm getPerm(@PathVariable Long id) {
        return service.findById(id);
    }
}
