package com.example.list.l12;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L12: get and list coexistence. Car has both a by-id findById (section 4.1) and a clean findAll, so
 * getCar and listCar are both discovered, independently. This proves the pools are disjoint and E4 is
 * additive: adding list discovery never perturbs by-id discovery.
 */
@Entity
class Car {
    @Id
    Long id;
    String make;
}

interface CarRepository extends JpaRepository<Car, Long> {
}

@Service
class CarService {

    private final CarRepository repo;

    CarService(CarRepository repo) {
        this.repo = repo;
    }

    public Car findById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    public List<Car> findAll() {
        return repo.findAll();
    }
}
