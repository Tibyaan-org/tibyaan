package com.example.attestshape;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The AT-2 CATEGORY B (SHAPE) reference shapes (fixtures ATT-2, plus the category B refusal battery).
 * Every read here is REFUSED by the structural rules, so none mints a verified capability. What each one
 * exercises is the FRAME a shape assertion must sit inside (Volume 5 section 4.15.2, category B): the
 * local-tie precondition, the asserted subject entity's existence, structural non-contradiction, and the
 * by-primary-key obligation that comes with claiming the reserved id mark.
 */
@Entity
class Building {
    @Id
    Long id;
    String name;
    Long ownerId;
}

@Entity
class Unit {
    @Id
    Long id;
    Long buildingId;
}

/** NOT an entity: the projection element type the view machinery cannot resolve to a nameable entity. */
class UnitSummary {
}

class BuildingView {
}

interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findAllByOwnerId(Long ownerId);
}

interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findAllByBuildingId(Long buildingId);

    List<UnitSummary> findSummaries();
}

class BuildingMapper {

    BuildingView toView(Building building, List<Unit> units) {
        return null;
    }
}

class SummaryResultSet {

    SummaryResultSet(List<?> data) {
    }
}

/** The unseen side of a delegation: no in-tree persistence, which is exactly why it is category C. */
class RemoteCatalogClient {

    BuildingView fetchBuilding(Long id) {
        return null;
    }
}

@Service
class BuildingServiceImpl {

    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final BuildingMapper mapper;
    private final RemoteCatalogClient catalog;

    BuildingServiceImpl(BuildingRepository buildingRepository, UnitRepository unitRepository,
                        BuildingMapper mapper, RemoteCatalogClient catalog) {
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.mapper = mapper;
        this.catalog = catalog;
    }

    // ATT-2a, THE AGGREGATE. A clean by-primary-key load of Building, then an enrichment access, then a
    // composite mapper. Section 4.2 refuses it at condition 6 (a second repository access), and section
    // 4.1.1 refuses the entity-returning twin for the same reason. Attestable as SHAPE with the id mark:
    // the assertion covers the enrichment, and the tool still proves the load.
    public BuildingView getBuilding(Long id) {
        Building building = buildingRepository.findById(id).orElseThrow();
        List<Unit> units = unitRepository.findAllByBuildingId(id);
        return mapper.toView(building, units);
    }

    // ATT-2b, THE OPAQUE WRAPPER over an in-tree repository access. The wrapper's element type is not an
    // in-tree entity, so no structural rule can tie the output; the repository access IS in-tree, so the
    // local-tie precondition holds and the read-safety check had something real to inspect.
    public SummaryResultSet getUnitSummaries() {
        return new SummaryResultSet(unitRepository.findSummaries());
    }

    // REFUSAL, no local tie. The real work is a delegation the tool never sees, so read-safety over the
    // slice is vacuous and only a human's own read-only affirmation could carry it. That is category C
    // (AT-3), deferred by name; it is never accepted as shape.
    public BuildingView getRemoteBuilding(Long id) {
        return catalog.fetchBuilding(id);
    }

    // REFUSAL, structural contradiction. The body's ONLY repository access provably reads Unit, so an
    // entry asserting that this read returns Building is contradicted by the structure the tool can see.
    public SummaryResultSet getUnitBundle(Long buildingId) {
        return new SummaryResultSet(unitRepository.findAllByBuildingId(buildingId));
    }

    // REFUSAL, the id-mark obligation. The single parameter is a FOREIGN key and the access is a filter
    // query, so an entry that marks it with the reserved id mark claims a by-primary-key read the body
    // does not perform. The mark means a proven identifier, never an asserted one.
    public BuildingView getBuildingByOwner(Long ownerId) {
        List<Building> found = buildingRepository.findAllByOwnerId(ownerId);
        return mapper.toView(found.get(0), List.of());
    }

    // REFUSAL, read-safety. A shape assertion does not soften the mutation check: a visible write refuses
    // the attestation exactly as it does on the predicate path.
    public SummaryResultSet getUnitSummariesUnsafe() {
        unitRepository.deleteById(1L);
        return new SummaryResultSet(unitRepository.findSummaries());
    }
}
