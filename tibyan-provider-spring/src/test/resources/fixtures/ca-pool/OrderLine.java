package ca.pool;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class OrderLine {
    @Id
    Long id;
}

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
}

@Service
class OrderLineService {
    private final OrderLineRepository orderLineRepository;

    OrderLineService(OrderLineRepository orderLineRepository) {
        this.orderLineRepository = orderLineRepository;
    }

    public Page<OrderLine> findAll(Pageable pageable) {
        return orderLineRepository.findAll(pageable);
    }
}

@RestController
class OrderLineController {
    private final OrderLineService orderLineService;

    OrderLineController(OrderLineService orderLineService) {
        this.orderLineService = orderLineService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_LIST')")
    public Page<OrderLine> listOrderLines(Pageable pageable) {
        return orderLineService.findAll(pageable);
    }
}
