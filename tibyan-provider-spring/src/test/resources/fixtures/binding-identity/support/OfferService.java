package example.support.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface SupportOfferJpaRepository extends JpaRepository<Offer, Long> {
}

/**
 * Test fixture (not compiled), BI-2. A clean by-id read of the support Offer on a service whose SIMPLE name,
 * OfferService, is shared with the crm context's service. Before Binding Identity both bindings read
 * OfferService#getById and rule 7 refused the model; now each binding carries its qualified class and
 * both emit (Volume 7 section 12.3).
 */
@Service
public class OfferService {

    private final SupportOfferJpaRepository repo;

    OfferService(SupportOfferJpaRepository repo) {
        this.repo = repo;
    }

    public Offer getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
