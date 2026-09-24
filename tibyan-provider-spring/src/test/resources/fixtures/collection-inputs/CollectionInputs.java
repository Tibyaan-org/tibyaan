package com.example.coll;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Phase DL-2a reference shapes (fixtures CI-1 to CI-6, Volume 5 section 4.19.2). One entity, one repository, one
 * service whose public methods cover every collection-input outcome: a List of a primitive, a Set of a
 * primitive, a Collection of an in-tree enum, a raw List, a wildcard element, and a nested collection. Expected
 * outcomes are in CollectionInputProviderTest.
 */
@Entity
class Ticket {
    @Id
    Long id;
    String code;
    TicketStatus status;
}

/** A bare in-tree enum: its constant names are the wire values (section 4.16.4, shape a). */
enum TicketStatus {
    OPEN,
    CLOSED
}

interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByIdIn(List<Long> ids);

    List<Ticket> findByCodeIn(Set<String> codes);

    List<Ticket> findByStatusIn(Collection<TicketStatus> statuses);
}

@Service
class TicketServiceImpl {

    private final TicketRepository repo;

    TicketServiceImpl(TicketRepository repo) {
        this.repo = repo;
    }

    // CI-1: List<Long> binds as LONG, multiple.
    public List<Ticket> byIds(List<Long> ids) {
        return repo.findByIdIn(ids);
    }

    // CI-2: Set<String> binds as STRING, multiple.
    public List<Ticket> byCodes(Set<String> codes) {
        return repo.findByCodeIn(codes);
    }

    // CI-3: Collection of an in-tree enum binds as STRING, multiple, carrying the closed set.
    public List<Ticket> byStatuses(Collection<TicketStatus> statuses) {
        return repo.findByStatusIn(statuses);
    }

    // CI-4: a raw List names no element type and refuses.
    public List<Ticket> rawIds(List ids) {
        return repo.findByIdIn(ids);
    }

    // CI-5: a wildcard element names no one type and refuses.
    public List<Ticket> wildIds(List<? extends Number> ids) {
        return List.of();
    }

    // CI-6: a nested collection refuses.
    public List<Ticket> nestedIds(List<List<Long>> ids) {
        return List.of();
    }
}
