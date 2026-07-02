package com.example.list.l6;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L6: unfiltered-looking but body-filtered. findCompleted takes no parameters, so it looks like a
 * list-all, but its body post-filters the framework result. The returned expression is not the bare
 * findAll, so it is refused (nothing may be wrapped around findAll); a filtered subset never binds to
 * listOrder.
 */
@Entity
class Order {
    @Id
    Long id;
    boolean done;
}

interface OrderRepository extends JpaRepository<Order, Long> {
}

@Service
class OrderService {

    private final OrderRepository repo;

    OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public List<Order> findCompleted() {
        return repo.findAll().stream().filter(Order::isDone).toList();
    }
}
