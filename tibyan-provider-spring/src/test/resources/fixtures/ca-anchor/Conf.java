package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Conf {
    @Id
    Long id;
}

@Service
class ConfService {
    public Conf findById(Long id) {
        return null;
    }
}

@RestController
class ConfControllerA {
    private final ConfService confService;

    ConfControllerA(ConfService confService) {
        this.confService = confService;
    }

    @GetMapping("/a/{id}")
    @PreAuthorize("hasAuthority('CONF_A')")
    public Conf getConfA(@PathVariable Long id) {
        return confService.findById(id);
    }
}

@RestController
class ConfControllerB {
    private final ConfService confService;

    ConfControllerB(ConfService confService) {
        this.confService = confService;
    }

    @GetMapping("/b/{id}")
    @PreAuthorize("hasAuthority('CONF_B')")
    public Conf getConfB(@PathVariable Long id) {
        return confService.findById(id);
    }
}
