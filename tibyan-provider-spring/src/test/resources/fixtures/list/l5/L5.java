package com.example.list.l5;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * L5: filtered read. A domain parameter (categoryId) is present, so the meaning of the read cannot be
 * verified structurally. It is deferred, not modeled, and the parameter name is never copied into an
 * input: no listCat is minted, and no capability ever carries an input named categoryId.
 */
@Entity
class Cat {
    @Id
    Long id;
    String name;
}

interface CatRepository extends JpaRepository<Cat, Long> {
    Page<Cat> findByCategoryId(UUID categoryId, Pageable pageable);
}

@Service
class CatService {

    private final CatRepository repo;

    CatService(CatRepository repo) {
        this.repo = repo;
    }

    public Page<Cat> findByCategory(UUID categoryId, Pageable pageable) {
        return repo.findByCategoryId(categoryId, pageable);
    }
}
