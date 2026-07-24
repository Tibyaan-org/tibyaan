package com.example.p1b.verified;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * P1b end to end, verified: the load is NOT a framework by-primary-key read (findById) but a custom
 * {@code @Query} that P1b proves is a nothing-but-a-by-pk select, with a LEFT JOIN FETCH only. The read
 * discovers exactly as a framework load would. This is the wiring proof for section 4.2.10; the token
 * matcher's condition coverage lives in P1bQueryMatcherTest.
 */
@Entity
class Alpha {
    @Id
    Long id;
    String name;
}

interface AlphaRepository extends JpaRepository<Alpha, Long> {

    @Query("SELECT a FROM Alpha a LEFT JOIN FETCH a.detail WHERE a.id = :id")
    Optional<Alpha> findByIdWithDetail(@Param("id") Long id);
}

class AlphaMapper {
    AlphaVto toVto(Alpha alpha) {
        return null;
    }
}

class AlphaVto {
}

@Service
class AlphaService {

    private final AlphaRepository repo;
    private final AlphaMapper mapper;

    AlphaService(AlphaRepository repo, AlphaMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public AlphaVto getById(Long id) {
        return mapper.toVto(repo.findByIdWithDetail(id).orElseThrow());
    }
}
