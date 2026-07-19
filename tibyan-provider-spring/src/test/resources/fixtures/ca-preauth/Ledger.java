package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

// CA-3: a class-level @PreAuthorize with no method-level one applies to the handler and compiles.
@Entity
class Ledger {
    @Id
    Long id;
}

@Service
class LedgerService {
    public Ledger findById(Long id) {
        return null;
    }
}

@RestController
@PreAuthorize("hasAuthority('LEDGER_READ')")
class LedgerController {
    private final LedgerService ledgerService;

    LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{id}")
    public Ledger getLedger(@PathVariable Long id) {
        return ledgerService.findById(id);
    }
}
