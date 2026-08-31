package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

// CA-3b: class-level plus a different method-level @PreAuthorize; the method-level wins alone.
@Entity
class Audit {
    @Id
    Long id;
}

interface AuditJpaRepo extends JpaRepository<Audit, Long> {
}

@Service
class AuditService {

    private final AuditJpaRepo repo;
    public Audit findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
@PreAuthorize("hasAuthority('CLASS_LEVEL')")
class AuditController {
    private final AuditService auditService;

    AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('METHOD_LEVEL')")
    public Audit getAudit(@PathVariable Long id) {
        return auditService.findById(id);
    }
}
