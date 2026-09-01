package com.example.enuminputs;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The Enum Inputs reference shapes (fixtures EI-1 to EI-9, Volume 5 section 4.16.8). Every read here is a
 * filtered list read, so all of them are structurally REFUSED and none mints a verified capability; each
 * becomes attestable as a PREDICATE, which is what makes the value set observable. The point of the file is
 * the ENUM DECLARATIONS: which ones the tool can read a wire-value set from, and which ones it declines.
 */
@Entity
class Ticket {
    @Id
    Long id;
    String title;
}

interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStatus(TicketStatus status, Integer pageNum);

    List<Ticket> findByCategory(CostCategory category);

    List<Ticket> findByLease(LeaseStatus leaseStatus);

    List<Ticket> findByLogic(LogicMapped value);

    List<Ticket> findByTwo(TwoValues value);

    List<Ticket> findByNonLiteral(NonLiteral value);

    List<Ticket> findByExternal(java.time.DayOfWeek day);

    List<Ticket> findByRaw(String raw);
}

class TicketResultSet {
    TicketResultSet(List<Ticket> data) {
    }
}

/** EI-1, SHAPE (a), BARE ENUM: no @JsonValue anywhere, so the wire form is the CONSTANT NAME. */
enum TicketStatus {
    OPEN,
    ASSIGNED,
    DOING,
    DONE
}

/** EI-2, SHAPE (b), VALUE-MAPPED: the @JsonValue accessor proves the field IS the wire form. */
enum CostCategory {
    MAINTENANCE("maintenance"),
    UTILITIES("utilities"),
    OTHER("other");

    private final String value;

    CostCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}

/**
 * EI-3, THE CONSTRUCTOR-LITERAL TRAP, the most important negative in this file. The literals are decorative
 * labels and there is NO @JsonValue, so Jackson serializes the CONSTANT NAME. A rule that read constructor
 * literals whenever it saw them would advertise "Draft", every value of which the application rejects.
 */
enum LeaseStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    ENDED("Ended");

    private final String label;

    LeaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

/** EI-7, SHAPE (c): @JsonValue on a method with logic. Not readable as the wire form; refuse. */
enum LogicMapped {
    ONE("one"),
    TWO("two");

    private final String value;

    LogicMapped(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value.toUpperCase();
    }
}

/** EI-7, SHAPE (c): two @JsonValue members, so which one is the wire form is not readable; refuse. */
enum TwoValues {
    A("a", "x"),
    B("b", "y");

    private final String first;
    private final String second;

    TwoValues(String first, String second) {
        this.first = first;
        this.second = second;
    }

    @JsonValue
    public String getFirst() {
        return first;
    }

    @JsonValue
    public String getSecond() {
        return second;
    }
}

/** EI-7, SHAPE (c): a constant passes a non-literal, which the tool will not evaluate; refuse. */
enum NonLiteral {
    KNOWN("known"),
    COMPUTED(NonLiteral.PREFIX);

    static final String PREFIX = "computed";

    private final String value;

    NonLiteral(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}

@Service
class TicketServiceImpl {

    private final TicketRepository ticketRepository;

    TicketServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // EI-1: the bare enum carries its constant names.
    public TicketResultSet byStatus(TicketStatus status, Integer pageNum) {
        return new TicketResultSet(ticketRepository.findByStatus(status, pageNum));
    }

    // EI-2: the value-mapped enum carries its literals, not its constant names.
    public TicketResultSet byCategory(CostCategory category) {
        return new TicketResultSet(ticketRepository.findByCategory(category));
    }

    // EI-3: the trap. Carries DRAFT, ACTIVE, ENDED, never Draft, Active, Ended.
    public TicketResultSet byLease(LeaseStatus leaseStatus) {
        return new TicketResultSet(ticketRepository.findByLease(leaseStatus));
    }

    // EI-7: each of these refuses the capability rather than advertising a set the tool cannot read.
    public TicketResultSet byLogic(LogicMapped value) {
        return new TicketResultSet(ticketRepository.findByLogic(value));
    }

    public TicketResultSet byTwo(TwoValues value) {
        return new TicketResultSet(ticketRepository.findByTwo(value));
    }

    public TicketResultSet byNonLiteral(NonLiteral value) {
        return new TicketResultSet(ticketRepository.findByNonLiteral(value));
    }

    // EI-5: an enum from a binary dependency. EI-1 fails, the constants cannot be read, the capability
    // refuses rather than shipping an unconstrained input the application will reject values for.
    public TicketResultSet byExternal(java.time.DayOfWeek day) {
        return new TicketResultSet(ticketRepository.findByExternal(day));
    }

    // EI-6: the STRING-PARSED-INTERNALLY case. The closed set is not in the signature, so the tool carries
    // NO constraint and does NOT refuse: the declared contract genuinely is a string, and this is the one
    // place an unconstrained STRING is the truthful rendering.
    public TicketResultSet byRawStatus(String raw) {
        TicketStatus parsed = TicketStatus.valueOf(raw);
        return new TicketResultSet(ticketRepository.findByStatus(parsed, 0));
    }
}
