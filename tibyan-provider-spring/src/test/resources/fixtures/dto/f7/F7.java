package com.example.dto.f7;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * F7: a write in disguise. A by-primary-key load is followed by a mutation and a save before the
 * mapped return. Condition 6 refuses on the mutation access; a read binds to no write.
 */
@Entity
class Token {
    @Id
    Long id;
    boolean used;
}

interface TokenJpaRepository extends JpaRepository<Token, Long> {
}

class TokenMapper {
    TokenVto toVto(Token token) {
        return null;
    }
}

class TokenVto {
}

@Service
class TokenService {

    private final TokenJpaRepository repo;
    private final TokenMapper mapper;

    TokenService(TokenJpaRepository repo, TokenMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public TokenVto findById(Long id) {
        Token token = repo.findById(id).orElseThrow();
        token.used = true;
        return mapper.toVto(repo.save(token));
    }
}
