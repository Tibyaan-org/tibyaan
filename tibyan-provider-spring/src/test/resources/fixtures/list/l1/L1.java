package com.example.list.l1;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L1: bare findAll(), no parameters. The body is exactly {@code return repo.findAll();} on a Spring
 * Data repository of the entity, so conditions 1 to 7 hold. Discovers listAlpha, not paged.
 */
@Entity
class Alpha {
    @Id
    Long id;
    String name;
}

interface AlphaRepository extends JpaRepository<Alpha, Long> {
}

@Service
class AlphaService {

    private final AlphaRepository repo;

    AlphaService(AlphaRepository repo) {
        this.repo = repo;
    }

    public List<Alpha> findAll() {
        return repo.findAll();
    }
}
