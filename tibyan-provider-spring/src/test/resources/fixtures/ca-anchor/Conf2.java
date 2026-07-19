package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Conf2 {
    @Id
    Long id;
}

@Service
class Conf2Service {
    public Conf2 findById(Long id) {
        return null;
    }
}

@RestController
class Conf2ControllerA {
    private final Conf2Service conf2Service;

    Conf2ControllerA(Conf2Service conf2Service) {
        this.conf2Service = conf2Service;
    }

    @GetMapping("/a/{id}")
    @PreAuthorize("hasAuthority('CONF2_A')")
    public Conf2 getConf2A(@PathVariable Long id) {
        return conf2Service.findById(id);
    }
}

@RestController
class Conf2ControllerB {
    private final Conf2Service conf2Service;

    Conf2ControllerB(Conf2Service conf2Service) {
        this.conf2Service = conf2Service;
    }

    @GetMapping("/b/{id}")
    public Conf2 getConf2B(@PathVariable Long id) {
        return conf2Service.findById(id);
    }
}
