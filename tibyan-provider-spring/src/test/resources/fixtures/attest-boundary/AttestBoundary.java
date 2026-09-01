package com.example.attestboundary;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The AT-3 CATEGORY C (BOUNDARY) reference shapes, member C2 (Volume 5, section 4.15.2.3). Every read here
 * is REFUSED by the structural rules, so none mints a verified capability.
 *
 * <p>What makes this file category C rather than category B is what it does NOT contain: the delegating
 * reads have no in-tree persistence access at all, so the read-safety check has almost nothing to inspect
 * and the READ-ONLY property rests on a human's affirmation rather than on structure. That is the whole
 * reason this category was deferred, and the local read at the bottom is here to prove the partition: it
 * has a tie, so it belongs to category B and a boundary entry over it must refuse.
 */
@Entity
class Invoice {
    @Id
    Long id;
    String number;
}

class InvoiceView {
}

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByNumber(String number);
}

/** The unseen side: a cross-service client whose body the tool never reads. */
class BillingClient {

    InvoiceView fetchInvoice(Long id) {
        return null;
    }

    List<InvoiceView> fetchAll() {
        return null;
    }
}

@Service
class InvoiceServiceImpl {

    private final BillingClient billing;
    private final InvoiceRepository invoiceRepository;

    InvoiceServiceImpl(BillingClient billing, InvoiceRepository invoiceRepository) {
        this.billing = billing;
        this.invoiceRepository = invoiceRepository;
    }

    // ATT-14, the C2 positive. A delegation-only read: no in-tree persistence access, so its read-safety
    // is structurally unprovable and only an explicit human affirmation can carry it.
    public InvoiceView getRemoteInvoice(Long id) {
        return billing.fetchInvoice(id);
    }

    // A second delegation, for the affirmation and asserter refusals so they do not collide on one target.
    public List<InvoiceView> listRemoteInvoices() {
        return billing.fetchAll();
    }

    // ATT-17, the partition guard. This read HAS an in-tree persistence access, so its read-safety IS
    // inspectable and it is a category B target. A boundary entry over it must refuse and name shape:
    // boundary must never become a way to skip category B's structural checks by wearing a label.
    public List<InvoiceView> findLocalByNumber(String number) {
        invoiceRepository.findAllByNumber(number);
        return List.of();
    }

    // A delegation whose body ALSO performs a visible write. Read-safety still runs on what the tool can
    // see, so this refuses before any affirmation is considered: the human's word covers the residue below
    // the slice, never a write the tool watched happen.
    public InvoiceView getRemoteInvoiceUnsafe(Long id) {
        invoiceRepository.deleteById(id);
        return billing.fetchInvoice(id);
    }
}
