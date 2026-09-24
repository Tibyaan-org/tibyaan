package com.example.shape;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Phase DL-2b reference shapes (fixtures SI-1 to SI-9 and SI-12, Volume 5 section 4.19.3). One entity, one enum,
 * a Lombok DTO with every field kind, a nested DTO, a record, and the three refusals: empty, not constructible,
 * self-referential. Expected outcomes are in ShapeInputProviderTest.
 */
@Entity
class Order {
    @Id
    Long id;
    String customer;
}

enum Priority {
    LOW,
    HIGH
}

/** SI-1, SI-3, SI-4, SI-5: a @Data DTO with a primitive, an enum, a nested DTO, a list, an entity and a map. */
@Data
class OrderDraft {
    private String customer;
    private Integer quantity;
    private Priority priority;
    private Address address;
    private List<String> tags;
    private Order order;
    private Map<String, Object> extra;
}

/** SI-4: the nested DTO. */
@Data
class Address {
    private String street;
    private String city;
}

/** SI-2: a record binds through its canonical constructor. */
record Coordinates(Double lat, Double lng) {
}

/** SI-6: no field the tool can model. */
@Data
class Empty {
    private Map<String, Object> attributes;
}

/** SI-7: an all-arguments constructor only; the runtime cannot build it from a JSON object. */
class Immutable {
    private final String value;

    Immutable(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

/** SI-9: self-referential. */
@Data
class Node {
    private String label;
    private Node parent;
}

interface OrderRepository extends JpaRepository<Order, Long> {
}

@Service
class OrderServiceImpl {

    private final OrderRepository repo;

    OrderServiceImpl(OrderRepository repo) {
        this.repo = repo;
    }

    // SI-1: WRITE taking the DTO.
    public Order create(OrderDraft draft) {
        Order order = new Order();
        order.customer = draft.getCustomer();
        return repo.save(order);
    }

    // SI-2: READ taking a record.
    public String locate(Coordinates coordinates) {
        return coordinates.lat() + "," + coordinates.lng();
    }

    // A list of the DTO binds as the shape, multiple.
    public List<Order> bulk(List<OrderDraft> drafts) {
        return List.of();
    }

    // SI-6
    public String empty(Empty empty) {
        return "";
    }

    // SI-7
    public String immutable(Immutable immutable) {
        return immutable.getValue();
    }

    // SI-9
    public String tree(Node node) {
        return node.getLabel();
    }

    // SI-8: an entity parameter is never an input.
    public Order attach(Order order) {
        return repo.save(order);
    }
}
