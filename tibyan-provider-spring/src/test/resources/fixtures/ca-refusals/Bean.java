package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Bean {
    @Id
    Long id;
}

@Service
class BeanService {
    public Bean findById(Long id) {
        return null;
    }
}

@RestController
class BeanController {
    private final BeanService service;

    BeanController(BeanService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.canRead(#id)")
    public Bean getBean(@PathVariable Long id) {
        return service.findById(id);
    }
}
