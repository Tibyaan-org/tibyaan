package com.example.review;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The DL-1 reviewed-draft reference shapes (fixtures RV-1 to RV-12, Volume 5 section 4.17.10). One entity, one
 * repository, one concrete service whose public methods cover every intake outcome: a discovered read-by-id
 * (superseded), a two-input read returning a DTO, a read with an in-tree enum input, a read through a one-hop
 * private helper (the label-drift edit point), a void method, a write, a read with a collection input, and a
 * read returning a primitive. Expected outcomes are in DraftReviewProviderTest.
 */
@Entity
class Invoice {
    @Id
    Long id;
    String number;
    InvoiceStatus status;
}

/** A bare in-tree enum: its constant names are the wire values (section 4.16.4, shape a). */
enum InvoiceStatus {
    OPEN,
    PAID,
    VOID
}

/** A DTO the tool does not model: a reviewed read returning it renders the permissive object (decision D5). */
class InvoiceSummary {
    InvoiceSummary(Invoice invoice, String currency) {
    }
}

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByNumberContaining(String fragment);

    List<Invoice> findByIdIn(List<Long> ids);
}

@Service
class InvoiceServiceImpl {

    private final InvoiceRepository repo;

    InvoiceServiceImpl(InvoiceRepository repo) {
        this.repo = repo;
    }

    // VERIFIED: the discovered read-by-id of Invoice. An approved draft of it is SUPERSEDED (RV-7).
    public Invoice getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    // READ with two mapped inputs, returning a DTO: RV-1, the permissive output (RV-F, decision D5).
    public InvoiceSummary summarize(Long id, String currency) {
        Invoice invoice = repo.findById(id).orElseThrow();
        return new InvoiceSummary(invoice, currency);
    }

    // READ with an in-tree enum input, returning a list of the entity: RV-8's closed set, RV-F's entity tie.
    public List<Invoice> byStatus(InvoiceStatus status) {
        return repo.findByStatus(status);
    }

    // READ through a one-hop private helper. The helper is inside the labeled slice but outside the
    // fingerprinted one, so a write added to it is LABEL_DRIFT, not STALE_DRAFT (RV-6).
    public List<Invoice> search(String fragment) {
        return repo.findByNumberContaining(normalize(fragment));
    }

    // UNKNOWN: returns void with no persistence mutation visible. Approved, it is kind ACTION (RV-3, D4).
    public void reindex(Long id) {
        repo.findById(id);
    }

    // WRITE with a String input: a write-set call on the repository field (R1). Approved, kind WRITE (RV-2).
    public Invoice open(String number) {
        Invoice invoice = new Invoice();
        invoice.number = number;
        return repo.save(invoice);
    }

    // READ with a collection input: INPUT_UNMAPPED, deferred to DL-2 by name (RV-8, decision D6).
    public List<Invoice> byIds(List<Long> ids) {
        return repo.findByIdIn(ids);
    }

    // READ returning a primitive: the output maps (RV-F).
    public long countByStatus(InvoiceStatus status) {
        return repo.findByStatus(status).size();
    }

    private String normalize(String fragment) {
        return fragment.trim();
    }
}
