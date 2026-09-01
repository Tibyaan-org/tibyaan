package com.example.attestboth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The COMPOUND category reference shapes (Volume 5, section 4.15.3). Every read here is REFUSED by the
 * structural rules, so none mints a verified capability.
 *
 * <p>What makes a target compound is having BOTH holes at once: a domain filter parameter whose meaning
 * structure cannot read (the predicate hole) AND a return whose element type does not resolve to an
 * in-tree entity (the shape hole). One entry fills both or the target stays refused; the three
 * single-hole reads below exist to prove the category must MATCH the holes, in both directions.
 */
@Entity
class Ticket {
    @Id
    Long id;
    String subject;
}

/** NOT an entity: the projection element the view machinery cannot resolve, which is the shape hole. */
class TicketSummary {
}

class TicketSummaryResultSet {
    TicketSummaryResultSet(List<TicketSummary> data) {
    }
}

interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<TicketSummary> findSummariesByOwner(String owner);

    List<TicketSummary> findAllSummaries();

    List<Ticket> findByOwner(String owner);
}

@Service
class TicketServiceImpl {

    private final TicketRepository ticketRepository;

    TicketServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // THE COMPOUND POSITIVE. Two holes at once: 'owner' is a domain filter whose meaning is not readable,
    // and the returned element type is not an in-tree entity. One entry fills both.
    public TicketSummaryResultSet searchSummaries(String owner) {
        return new TicketSummaryResultSet(ticketRepository.findSummariesByOwner(owner));
    }

    // SHAPE HOLE ONLY: no domain parameter at all, so there is no predicate hole for a compound entry to
    // fill. A 'both' entry here must refuse and name shape.
    public TicketSummaryResultSet allSummaries() {
        return new TicketSummaryResultSet(ticketRepository.findAllSummaries());
    }

    // PREDICATE HOLE ONLY: the element type IS an in-tree entity, so the output tie is provable and the
    // subject is a fact the tool established. A 'both' entry here must refuse and name predicate.
    public List<Ticket> byOwner(String owner) {
        return ticketRepository.findByOwner(owner);
    }
}
