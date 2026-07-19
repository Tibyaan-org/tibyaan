package ca.secured;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.annotation.Secured;

import static ca.secured.Scopes.BAD_CONST;

@Entity
class BadConst {
    @Id
    Long id;
}

@Service
class BadConstService {
    public BadConst findById(Long id) {
        return null;
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
