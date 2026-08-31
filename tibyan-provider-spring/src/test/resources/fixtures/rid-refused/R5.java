package rid.refused.r5;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-5. A write behind a read-shaped signature: the body saves before
 * it loads. Refuses at condition 6, the write-set clause, which is section 4.2 condition 6 applied
 * verbatim on this path. A read binds to no write.
 */
@Entity
class Ledger {
    @Id
    Long id;
    String note;
}

interface LedgerJpaRepo extends JpaRepository<Ledger, Long> {
}

@Service
class LedgerService {

    private final LedgerJpaRepo repo;

    public Ledger getById(Long id) {
        repo.save(new Ledger());
        return repo.findById(id).orElseThrow();
    }
}
