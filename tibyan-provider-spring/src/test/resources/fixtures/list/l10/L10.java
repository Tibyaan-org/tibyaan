package com.example.list.l10;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L10: the structurally-indistinguishable pair. Pair has both a bare findAll() and a findAll(Pageable),
 * both clean. Because paging is unmodeled, they produce the identical capability, so the tool refuses
 * as LIST_AMBIGUOUS rather than prefer one. No listPair is minted.
 */
@Entity
class Pair {
    @Id
    Long id;
    String name;
}

interface PairRepository extends JpaRepository<Pair, Long> {
}

@Service
class PairService {

    private final PairRepository repo;

    PairService(PairRepository repo) {
        this.repo = repo;
    }

    public List<Pair> findAll() {
        return repo.findAll();
    }

    public Page<Pair> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }
}
