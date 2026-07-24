package com.example.p1b.refused;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * P1b end to end, refused on an inner JOIN FETCH: the pms Tenant shape. The custom {@code @Query} clears
 * the by-pk WHERE, but its inner JOIN FETCH is an existence filter that drops rows whose association is
 * absent, so it is not a faithful by-id load. The read stays refused; no capability is emitted.
 */
@Entity
class Beta {
    @Id
    Long id;
    String name;
}

interface BetaRepository extends JpaRepository<Beta, Long> {

    @Query("SELECT b FROM Beta b JOIN FETCH b.owner WHERE b.id = :id")
    Optional<Beta> findByIdWithOwner(@Param("id") Long id);
}

class BetaMapper {
    BetaVto toVto(Beta beta) {
        return null;
    }
}

class BetaVto {
}

@Service
class BetaService {

    private final BetaRepository repo;
    private final BetaMapper mapper;

    BetaService(BetaRepository repo, BetaMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public BetaVto getById(Long id) {
        return mapper.toVto(repo.findByIdWithOwner(id).orElseThrow());
    }
}
