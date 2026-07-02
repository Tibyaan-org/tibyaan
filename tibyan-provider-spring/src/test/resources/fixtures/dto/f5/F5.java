package com.example.dto.f5;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * F5: non-primary-key load. The body is otherwise a perfect fused read, but the load calls a
 * derived query (findByUserId) rather than a framework by-primary-key read. Condition 4 refuses.
 */
@Entity
class Account {
    @Id
    Long id;
    Long userId;
}

interface AccountJpaRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUserId(Long userId);
}

class AccountMapper {
    AccountVto toVto(Account account) {
        return null;
    }
}

class AccountVto {
}

@Service
class AccountService {

    private final AccountJpaRepository repo;
    private final AccountMapper mapper;

    AccountService(AccountJpaRepository repo, AccountMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public AccountVto findById(Long id) {
        return mapper.toVto(repo.findByUserId(id).orElseThrow());
    }
}
