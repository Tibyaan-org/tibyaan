package com.example.draft;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;

/**
 * Spike DL-0 fixture: one concrete service whose public methods cover every label but ATTESTED, plus an
 * overload pair for name minting. Expected labels are in DraftLabelerTest.
 */
@Service
public class OrderServiceImpl {

    private final OrderRepository repo;
    private final RemoteClient remote;
    private final EntityManager em;

    public OrderServiceImpl(OrderRepository repo, RemoteClient remote, EntityManager em) {
        this.repo = repo;
        this.remote = remote;
        this.em = em;
    }

    // VERIFIED: the discovered read-by-id of Order.
    public Order getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    // READ: a filtered read the discovery rules do not cover.
    public List<Order> search(String status) {
        return repo.findByStatus(status);
    }

    // READ, overload: minted name gets a suffix.
    public List<Order> search(String status, int limit) {
        return repo.findByStatus(status).subList(0, limit);
    }

    // READ with an unmapped input: the collection parameter maps to null.
    public int count(List<Long> ids) {
        return ids.size();
    }

    // WRITE, R1: a write-set call on the repository field.
    public Order create(Order order) {
        return repo.save(order);
    }

    // WRITE: a setter on an entity loaded from the repository.
    public void archive(Long id) {
        Order order = repo.findById(id).orElseThrow();
        order.setStatus("ARCHIVED");
    }

    // WRITE: a @Modifying repository method.
    public int closeAll(String status) {
        return repo.closeAll(status);
    }

    // WRITE through a one-hop private helper.
    public void purge(Long id) {
        deleteQuietly(id);
    }

    // WRITE, R3: a write-set-named call on a non-repository field.
    public void sync(Long id) {
        remote.remove(id);
    }

    // WRITE, R2: a write-set call on the persistence-infrastructure field.
    public void flushAll() {
        em.flush();
    }

    // UNKNOWN: a non-write-set call on the persistence-infrastructure field.
    public List<?> raw(String jpql) {
        return em.createQuery(jpql).getResultList();
    }

    // READ (known blind spot): a call into another bean that is not write-set-named passes.
    public String describe(Long id) {
        return remote.fetch(id);
    }

    // UNKNOWN: returns void with no persistence mutation visible; a side effect elsewhere is not analyzed.
    public void notify(Long id) {
        remote.fetch(id);
    }

    private void deleteQuietly(Long id) {
        repo.deleteById(id);
    }

    public static OrderServiceImpl of(OrderRepository repo) {
        return new OrderServiceImpl(repo, null, null);
    }
}
