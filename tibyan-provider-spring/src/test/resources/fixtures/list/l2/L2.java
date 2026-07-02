package com.example.list.l2;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * L2: paged findAll(Pageable). The body forwards the Pageable verbatim to the framework findAll on a
 * Spring Data repository of the entity. Discovers listBeta, and the wire is a page envelope, so the
 * capability carries the paged annotation.
 */
@Entity
class Beta {
    @Id
    Long id;
    String name;
}

interface BetaRepository extends JpaRepository<Beta, Long> {
}

@Service
class BetaService {

    private final BetaRepository repo;

    BetaService(BetaRepository repo) {
        this.repo = repo;
    }

    public Page<Beta> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }
}
