package com.example.list.l3;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * L3: container variety. A Set return and a Slice return both map to the same outputMultiple output
 * (the container does not change the IR). Set is not an envelope, so listGamma is not paged; Slice is
 * an envelope, so listDelta is paged.
 */
@Entity
class Gamma {
    @Id
    Long id;
    String name;
}

@Entity
class Delta {
    @Id
    Long id;
    String name;
}

interface GammaRepository extends JpaRepository<Gamma, Long> {
}

interface DeltaRepository extends JpaRepository<Delta, Long> {
}

@Service
class GammaService {

    private final GammaRepository repo;

    GammaService(GammaRepository repo) {
        this.repo = repo;
    }

    public Set<Gamma> findAll() {
        return repo.findAll();
    }
}

@Service
class DeltaService {

    private final DeltaRepository repo;

    DeltaService(DeltaRepository repo) {
        this.repo = repo;
    }

    public Slice<Delta> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }
}
