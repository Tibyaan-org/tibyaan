package com.example.e6a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * E6a-4: the name trap. The wrapper return type is named TenantResultSet, but the body's single
 * persistence access is on a Spring Data repository of Building. The tie is to Building, proven by the
 * repository access, never to Tenant, which the wrapper's name alone would suggest. The read is classified
 * as a read of Building (and deferred, since the wrapper is non-generic and its collection-ness is not
 * provable), never Tenant. E6a-5's mis-classification guard is folded in: the classified entity is exactly
 * the one the access proves, never a sibling.
 */
@Entity
class Building {
    @Id
    Long id;
    String code;
}

class TenantResultSet {
}

interface BuildingRepository extends JpaRepository<Building, Long> {
}

class BuildingMapper {
    TenantResultSet toResultSet(List<Building> buildings) {
        return null;
    }
}

@Service
class BuildingService {

    private final BuildingRepository repo;
    private final BuildingMapper mapper;

    BuildingService(BuildingRepository repo, BuildingMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public TenantResultSet getBuildings() {
        return mapper.toResultSet(repo.findAll());
    }
}
