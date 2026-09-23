package com.example.closure;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.mapstruct.Mapper;

/** DL-1.1 fixtures LC-1 to LC-7 (Volume 5 section 4.17.14). Expected labels are in BeanCallClosureTest. */
@Entity
class Ticket {
    @Id
    private Long id;
    private String title;
    public Long getId() { return id; }
    public String getTitle() { return title; }
}

interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByTitle(String title);
}

interface TicketQueries {
    List<Ticket> byTitle(String title);
}

@Service
class TicketQueriesImpl implements TicketQueries {
    private final TicketRepository repo;
    TicketQueriesImpl(TicketRepository repo) { this.repo = repo; }
    public List<Ticket> byTitle(String title) { return repo.findByTitle(title); }
}

@Service
class Counter {
    private final TicketRepository repo;
    Counter(TicketRepository repo) { this.repo = repo; }
    public Long next() { return bump(); }
    private Long bump() { return repo.save(new Ticket()).getId(); }
}

@Component
class Publisher {
    private final JmsTemplate jms;
    Publisher(JmsTemplate jms) { this.jms = jms; }
    public void publish(String event) { jms.convertAndSend("topic", event); }
}

@FeignClient(name = "remote")
interface RemoteTickets {
    @GetMapping("/tickets/{id}")
    String get(Long id);
    @PostMapping("/tickets")
    String create(String title);
}

@Mapper
interface TicketMapper {
    String toTitle(Ticket ticket);
}

@Service
class Hop1 { private final Hop2 next; Hop1(Hop2 n) { next = n; } public String go() { return next.go(); } }
@Service
class Hop2 { private final Hop3 next; Hop2(Hop3 n) { next = n; } public String go() { return next.go(); } }
@Service
class Hop3 { private final Hop4 next; Hop3(Hop4 n) { next = n; } public String go() { return next.go(); } }
@Service
class Hop4 { private final Hop5 next; Hop4(Hop5 n) { next = n; } public String go() { return next.go(); } }
@Service
class Hop5 { public String go() { return "deep"; } }

@Service
public class TicketServiceImpl {

    private final TicketQueries queries;
    private final Counter counter;
    private final Publisher publisher;
    private final RemoteTickets remote;
    private final TicketMapper mapper;
    private final ApplicationContext context;
    private final Hop1 hops;

    public TicketServiceImpl(TicketQueries queries, Counter counter, Publisher publisher, RemoteTickets remote,
                             TicketMapper mapper, ApplicationContext context, Hop1 hops) {
        this.queries = queries;
        this.counter = counter;
        this.publisher = publisher;
        this.remote = remote;
        this.mapper = mapper;
        this.context = context;
        this.hops = hops;
    }

    // LC-1: an in-tree read-only bean through its interface stays READ.
    public List<Ticket> search(String title) { return queries.byTitle(title); }

    // LC-2: an in-tree bean whose method saves, through a private helper, is WRITE.
    public Long allocate() { return counter.next(); }

    // LC-3: a JmsTemplate behind a bean is WRITE.
    public String download(String title) { publisher.publish(title); return title; }

    // LC-4: a Feign GET is READ with the call named; a Feign POST is WRITE.
    public String remoteGet(Long id) { return remote.get(id); }
    public String remoteCreate(String title) { return remote.create(title); }

    // LC-5: reflection is UNKNOWN.
    public Object dispatch(String bean, String method) throws Exception {
        Object target = context.getBean(bean);
        Method m = target.getClass().getDeclaredMethod(method);
        return m.invoke(target);
    }

    // LC-6: a five-hop chain exceeds the bound of four.
    public String deep() { return hops.go(); }

    // LC-7: a MapStruct mapper call stays READ.
    public String title(Ticket ticket) { return mapper.toTitle(ticket); }
}
