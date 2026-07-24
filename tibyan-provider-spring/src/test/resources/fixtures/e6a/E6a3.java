package com.example.e6a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * E6a-3: an opaque wrapper. The body's one persistence-shaped access is on a repository whose element is
 * not an in-tree {@code @Entity} (GhostRecord is a plain class), so no structural tie to an entity holds:
 * neither the generic type argument (T1) nor the body access (T2) proves a read of an entity. The read is
 * refused OPAQUE_WRAPPER, counted, never guessed. The tool never reads the wrapper's fields or trusts its
 * name to invent a tie.
 */
@Entity
class Ghost {
    @Id
    Long id;
}

class GhostRecord {
}

class GhostResultSet {
}

interface GhostRepository extends JpaRepository<GhostRecord, Long> {
}

class GhostMapper {
    GhostResultSet toResultSet(List<GhostRecord> records) {
        return null;
    }
}

@Service
class GhostService {

    private final GhostRepository repo;
    private final GhostMapper mapper;

    GhostService(GhostRepository repo, GhostMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public GhostResultSet getGhosts() {
        return mapper.toResultSet(repo.findAll());
    }
}
