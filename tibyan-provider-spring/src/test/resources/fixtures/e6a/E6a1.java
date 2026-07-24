package com.example.e6a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * E6a-1: a transparent GENERIC wrapper whose single type argument is the entity, wrapping a clean
 * unfiltered framework findAll. This is the one shape E6a discovers: the collection payload is provable
 * from the type argument (T1) without reading the wrapper, and the read is a clean list-all. The
 * benchmarks contain no such case (the forecast says so); this fixture proves visibility CAN lead to
 * discovery when the underlying read is clean.
 */
@Entity
class Boxed {
    @Id
    Long id;
    String name;
}

class Box<T> {
    Box(List<T> items) {
    }
}

interface BoxedRepository extends JpaRepository<Boxed, Long> {
}

@Service
class BoxedService {

    private final BoxedRepository repo;

    BoxedService(BoxedRepository repo) {
        this.repo = repo;
    }

    public Box<Boxed> getAllBoxed() {
        return new Box<>(repo.findAll());
    }
}
