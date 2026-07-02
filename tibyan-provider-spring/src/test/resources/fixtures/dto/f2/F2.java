package com.example.dto.f2;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * F2: one-hop delegation, two-statement form (the Unit and Lease shape). The service calls a
 * hand-written repository whose single @Repository implementation delegates in one return statement
 * to a Spring Data by-primary-key read. Condition 4b holds. Discovers.
 */
@Entity
class Gadget {
    @Id
    Long id;
    String name;
}

interface GadgetRepository {
    Optional<Gadget> selectById(Long id);
}

interface GadgetJpaRepository extends JpaRepository<Gadget, Long> {
}

@Repository
class GadgetRepositoryImpl implements GadgetRepository {

    private final GadgetJpaRepository jpa;

    GadgetRepositoryImpl(GadgetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Gadget> selectById(Long id) {
        return jpa.findById(id);
    }
}

class GadgetMapper {
    GadgetVto toVto(Gadget gadget) {
        return null;
    }
}

class GadgetVto {
}

@Service
class GadgetService {

    private final GadgetRepository repo;
    private final GadgetMapper mapper;

    GadgetService(GadgetRepository repo, GadgetMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public GadgetVto findById(Long id) {
        Gadget gadget = repo.selectById(id).orElseThrow();
        return mapper.toVto(gadget);
    }
}
