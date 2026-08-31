package example.sales.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface OrderJpaRepository extends JpaRepository<Order, Long> {
}

/**
 * Test fixture (not compiled), EI-2. An entity-returning finder of Order, proving its
 * by-primary-key load (section 4.1.1).
 */
@Service
public class OrderService {

    private final OrderJpaRepository repo;

    OrderService(OrderJpaRepository repo) {
        this.repo = repo;
    }

    public Order getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
