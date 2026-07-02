package com.example.dto.f9;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * F9: navigation argument (the getMyUnit shape). The load is a correct by-primary-key read of the
 * entity, but the mapping step's argument is a navigation off the loaded entity, not the entity
 * itself. Condition 8 refuses: the sole argument must be the loaded entity verbatim.
 */
@Entity
class Holder {
    @Id
    Long id;
    String name;
}

class Inner {
}

interface HolderJpaRepository extends JpaRepository<Holder, Long> {
}

class HolderMapper {
    InnerVto toInner(Inner inner) {
        return null;
    }
}

class InnerVto {
}

@Service
class HolderService {

    private final HolderJpaRepository repo;
    private final HolderMapper mapper;

    HolderService(HolderJpaRepository repo, HolderMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public InnerVto findById(Long id) {
        Holder holder = repo.findById(id).orElseThrow();
        return mapper.toInner(holder.getInner());
    }
}
