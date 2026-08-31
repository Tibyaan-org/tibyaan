package com.example.banking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface AccountJpaRepository extends JpaRepository<Account, Long> {
}

/**
 * Test fixture (not compiled). The backing finder is {@code loadById}, and the class is
 * {@code AccountStore}, so the discovered binding must be {@code AccountStore#loadById}, which the
 * old convention could never have guessed. The other methods are decoys with the wrong shape and
 * must be filtered out by the matching rule (Volume 5 section 4.1). {@code loadById} proves its
 * by-primary-key load, which section 4.1.1 requires of the entity-returning path on top of the
 * shape.
 */
@Service
public class AccountStore {

    private final AccountJpaRepository repo;

    AccountStore(AccountJpaRepository repo) {
        this.repo = repo;
    }

    // Matches: returns the entity, takes one parameter of the id type, and loads by primary key.
    public Account loadById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    // Decoy: returns the entity but its parameter is not the id type.
    public Account create(String owner) {
        return null;
    }

    // Decoy: takes the id type but does not return the entity.
    public String describe(Long id) {
        return null;
    }
}
