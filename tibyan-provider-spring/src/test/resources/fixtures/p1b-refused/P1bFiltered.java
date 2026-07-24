package com.example.p1b.refused;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * P1b end to end, refused on a filtered WHERE (the defining refusal, P1b-4): the second predicate makes
 * this a filtered read, never a by-id load. The read stays refused; no capability is emitted.
 */
@Entity
class Gamma {
    @Id
    Long id;
    String status;
}

interface GammaRepository extends JpaRepository<Gamma, Long> {

    @Query("SELECT g FROM Gamma g WHERE g.id = :id AND g.status = 'ACTIVE'")
    Optional<Gamma> findActiveById(@Param("id") Long id);
}

class GammaMapper {
    GammaVto toVto(Gamma gamma) {
        return null;
    }
}

class GammaVto {
}

@Service
class GammaService {

    private final GammaRepository repo;
    private final GammaMapper mapper;

    GammaService(GammaRepository repo, GammaMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public GammaVto getById(Long id) {
        return mapper.toVto(repo.findActiveById(id).orElseThrow());
    }
}
