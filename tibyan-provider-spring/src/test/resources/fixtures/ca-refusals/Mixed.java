package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Mixed {
    @Id
    Long id;
}

@Service
class MixedService {
    public Mixed findById(Long id) {
        return null;
    }
}

@RestController
class MixedController {
    private final MixedService service;

    MixedController(MixedService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('A') or #id < 100")
    public Mixed getMixed(@PathVariable Long id) {
        return service.findById(id);
    }
}
