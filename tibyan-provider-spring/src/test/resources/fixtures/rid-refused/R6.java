package rid.refused.r6;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Test fixture (not compiled), RID-6. Aggregate enrichment returning the entity itself: a clean
 * by-primary-key load plus a second repository access. Refuses at condition 5, because a multi-read
 * assembly is not a single provable read. This is the shape that belongs to Attestation AT-2, not to
 * a discovery rule, and the refusal is what routes it there.
 */
@Entity
class Invoice {
    @Id
    Long id;
    String number;
}

@Entity
class InvoiceLine {
    @Id
    Long id;
    Long invoiceId;
}

interface InvoiceJpaRepo extends JpaRepository<Invoice, Long> {
}

interface InvoiceLineJpaRepo extends JpaRepository<InvoiceLine, Long> {
    List<InvoiceLine> findAllByInvoiceId(Long invoiceId);
}

@Service
class InvoiceService {

    private final InvoiceJpaRepo repo;
    private final InvoiceLineJpaRepo lineRepo;

    public Invoice getById(Long id) {
        Invoice invoice = repo.findById(id).orElseThrow();
        lineRepo.findAllByInvoiceId(id);
        return invoice;
    }
}
