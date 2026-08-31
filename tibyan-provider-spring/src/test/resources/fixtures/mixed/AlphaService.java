package com.example.mixed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface AlphaJpaRepo extends JpaRepository<Alpha, Long> {
}

/**
 * Test fixture (not compiled): the one service in this mixed model whose read is fully provable, so
 * Alpha is the only entity that yields a capability. The body is a direct Spring Data
 * by-primary-key load returned untransformed (Volume 5 section 4.1.1, conditions 5 to 9).
 */
@Service
public class AlphaService {

    private final AlphaJpaRepo repo;

    public Alpha findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
