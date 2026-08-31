package rid.refused.r7;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-7. The id does not reach the load verbatim: the body computes an
 * expression over the parameter. Refuses at condition 7. What the capability's caller supplies and
 * what the load receives must be the same value, or the capability's signature is a claim the body
 * does not keep.
 */
@Entity
class Ticket {
    @Id
    Long id;
    String subject;
}

interface TicketJpaRepo extends JpaRepository<Ticket, Long> {
}

@Service
class TicketService {

    private final TicketJpaRepo repo;

    public Ticket getById(Long id) {
        return repo.findById(id + 1).orElseThrow();
    }
}
