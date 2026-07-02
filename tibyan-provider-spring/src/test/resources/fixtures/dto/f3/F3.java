package com.example.dto.f3;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * F3: fused form with Optional.map and a method reference. The mapping step is a method reference
 * inside Optional.map (condition 8), and the load is a direct Spring Data by-primary-key read.
 * Discovers.
 */
@Entity
class Thing {
    @Id
    Long id;
    String name;
}

interface ThingJpaRepository extends JpaRepository<Thing, Long> {
}

class ThingMapper {
    ThingVto toVto(Thing thing) {
        return null;
    }
}

class ThingVto {
}

@Service
class ThingService {

    private final ThingJpaRepository repo;
    private final ThingMapper mapper;

    ThingService(ThingJpaRepository repo, ThingMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public ThingVto findById(Long id) {
        return repo.findById(id).map(mapper::toVto).orElseThrow();
    }
}
