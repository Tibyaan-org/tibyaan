package com.example.readsafety;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixtures RS-1 to RS-8 for the read-safety receiver-awareness refinement (Volume 5 section 4.15.2.1).
 *
 * <p>Every method here is a READ that the structural rules refuse (each is filtered or wrapper-returning),
 * so none mints a verified capability. What they exercise is the write-set clause's receiver classification:
 * which mutating-named calls are PROVABLY local (and so not writes to application state), and which refuse.
 * The refusal cases are the load-bearing half: a genuine persistence write must never be classified safe.
 */
@Entity
class Cost {
    @Id
    Long id;
    String category;

    List<String> getTags() {
        return null;
    }

    void setCategory(String category) {
    }
}

class ChartSeries {
    ChartSeries(Map<String, Double> points) {
    }
}

interface CostRepository extends JpaRepository<Cost, Long> {

    List<Cost> findByBuildingId(Long buildingId);

    // A non-write-set call that takes a collection: the vehicle for the L6 escape guard, which must be
    // reachable without the escaping call itself being a write.
    void attachAll(List<Cost> items);

    @Modifying
    @Query("UPDATE Cost c SET c.category = 'x'")
    void reclassifyAll();
}

// A hand-written repository: a repository TYPE (it has an @Repository implementation in-tree) but not a
// Spring Data one, so a read tied only through it has an unprovable element tie. RS-2 uses this to prove
// that passing read-safety grants nothing on its own.
interface LedgerRepository {

    List<Cost> selectAll();
}

@Repository
class LedgerRepositoryImpl implements LedgerRepository {

    @Override
    public List<Cost> selectAll() {
        return null;
    }
}

// An in-tree type deliberately named after an allowlisted JDK container: the L4 no-shadow guard (RS-5).
class TreeMap {

    void merge(String key, Double value) {
    }
}

// A non-allowlisted accumulator carrying a write-set method name (RS-5).
class Accumulator {

    void merge(String key, Double value) {
    }
}

class CostArchiver {

    void delete(Cost cost) {
    }
}

@Service
class CostReportServiceImpl {

    private final CostRepository costRepository;
    private final LedgerRepository ledgerRepository;
    private final CostArchiver costArchiver;
    private final EntityManager entityManager;

    CostReportServiceImpl(CostRepository costRepository, LedgerRepository ledgerRepository,
                          CostArchiver costArchiver, EntityManager entityManager) {
        this.costRepository = costRepository;
        this.ledgerRepository = ledgerRepository;
        this.costArchiver = costArchiver;
        this.entityManager = entityManager;
    }

    // RS-1. The pms aggregate shape. One load, then aggregation into a locally constructed map using
    // merge and remove (the two write-set names that occur on collections) and put (which is not in the
    // write set at all, included so the fixture reads like the real shape). No write to application state:
    // read-safety PASSES, all six conditions hold, and the entry attests.
    public ChartSeries totalsByCategory(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Cost cost : costs) {
            byCategory.merge(cost.category, 1.0, Double::sum);
        }
        byCategory.put("total", 0.0);
        byCategory.remove("unclassified");
        return new ChartSeries(byCategory);
    }

    // RS-2. The same provable-local aggregation, but tied only through the hand-written repository, so the
    // output element tie is unprovable. Read-safety PASSES and the entry still REFUSES, at the tie. Passing
    // read-safety is not discovery and not attestation; it only removes a false refusal.
    public ChartSeries ledgerTotals(Long buildingId) {
        List<Cost> rows = ledgerRepository.selectAll();
        Map<String, Double> byKey = new LinkedHashMap<>();
        for (Cost row : rows) {
            byKey.merge(row.category, 1.0, Double::sum);
        }
        return new ChartSeries(byKey);
    }

    // RS-3, THE PRIMARY SAFETY GUARD. An otherwise identical aggregate body that also deletes through the
    // repository. The local carve-out must never rescue it. REFUSES, R1.
    public ChartSeries totalsAndPurge(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Cost cost : costs) {
            byCategory.merge(cost.category, 1.0, Double::sum);
        }
        costRepository.delete(costs.get(0));
        return new ChartSeries(byCategory);
    }

    // RS-4a, the alias guard. A collection obtained from a loaded entity is possibly a managed collection,
    // so mutating it is a write JPA would persist. REFUSES, R5.
    public ChartSeries dropTag(Long id) {
        Cost cost = costRepository.findById(id).orElseThrow();
        List<String> tags = cost.getTags();
        tags.remove(0);
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-4b, the escape guard. The list IS freshly constructed, but it is handed to a repository before it
    // is mutated, so it may be managed by the time the mutation runs. REFUSES, L6 and R8.
    public ChartSeries attachThenTrim(Long buildingId) {
        List<Cost> batch = new ArrayList<>();
        costRepository.attachAll(batch);
        batch.remove(0);
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-5a, the non-allowlisted type guard. A freshly constructed in-tree accumulator is still not a
    // provable local container. REFUSES, L4 and R6.
    public ChartSeries customAccumulator(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Accumulator accumulator = new Accumulator();
        accumulator.merge("all", 1.0);
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-5b, the in-tree shadow guard. The constructed type's simple name is on the allowlist, but an
    // in-tree declaration carries that name, so the JDK identity is not proven. REFUSES, L4 and R6.
    public ChartSeries shadowedContainer(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        TreeMap byCategory = new TreeMap();
        byCategory.merge("all", 1.0);
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-6, the reassignment guard. The declarator is a fresh construction, but the name is reassigned, so
    // its origin at the mutation is no longer proven. REFUSES, L5 and R7.
    public ChartSeries reassignedAccumulator(Long buildingId, Long other) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        byCategory = pick(other);
        byCategory.merge("all", 1.0);
        return new ChartSeries(byCategory);
    }

    // RS-7, the other-field guard. A write-set call on a NON-repository, NON-infrastructure field. This is
    // the case that would have been silently admitted by copying the discovery path's repository-only
    // scoping. REFUSES, R3.
    public ChartSeries archiveThenTotal(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        costArchiver.delete(costs.get(0));
        return new ChartSeries(byCategory);
    }

    // RS-8a, MIS-SAFE: an EntityManager write alongside a legitimate local aggregation. REFUSES, R2.
    public ChartSeries persistThenTotal(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        byCategory.merge("all", 1.0);
        entityManager.merge(costs.get(0));
        return new ChartSeries(byCategory);
    }

    // RS-8b, MIS-SAFE: a setter on a loaded entity (JPA dirty checking persists it) alongside the same
    // legitimate local aggregation. REFUSES on the setter clause, which the carve-out never touches.
    public ChartSeries retagThenTotal(Long id) {
        Cost cost = costRepository.findById(id).orElseThrow();
        Map<String, Double> byCategory = new LinkedHashMap<>();
        byCategory.merge("all", 1.0);
        cost.setCategory("x");
        return new ChartSeries(byCategory);
    }

    // RS-8c, MIS-SAFE: a @Modifying repository method, whose name is NOT in the write set, alongside the
    // same legitimate local aggregation. REFUSES on the @Modifying clause.
    public ChartSeries reclassifyThenTotal(Long buildingId) {
        List<Cost> costs = costRepository.findByBuildingId(buildingId);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        byCategory.merge("all", 1.0);
        costRepository.reclassifyAll();
        return new ChartSeries(byCategory);
    }

    // RS-9, the unqualified-call guard. A write-set call with no receiver is a call on the enclosing type,
    // never a local. REFUSES, R9.
    public ChartSeries selfWrite(Long buildingId) {
        Map<String, Double> byCategory = new LinkedHashMap<>();
        remove(buildingId);
        return new ChartSeries(byCategory);
    }

    // RS-10, the chained-receiver guard. The receiver expression is a call result, not a bare name, so its
    // origin is not directly traceable. REFUSES, L1 and R9.
    public ChartSeries chainedReceiver(Long buildingId) {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        byKey.get("a").remove(0);
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-11, the for-each variable guard. A loop variable is not a plain local declaration. REFUSES, L2.
    public ChartSeries loopVariableReceiver(Long buildingId) {
        List<List<String>> groups = new ArrayList<>();
        for (List<String> group : groups) {
            group.remove(0);
        }
        return new ChartSeries(new LinkedHashMap<>());
    }

    // RS-12, the parameter-aliased guard. The local is a fresh-looking name but its initializer is a
    // parameter, so its origin is outside the method. REFUSES, R4.
    ChartSeries aliasedParameter(Map<String, Double> seed) {
        Map<String, Double> byCategory = seed;
        byCategory.merge("all", 1.0);
        return new ChartSeries(byCategory);
    }

    // RS-13, the direct parameter guard. REFUSES, R4.
    ChartSeries parameterReceiver(Map<String, Double> seed) {
        seed.merge("all", 1.0);
        return new ChartSeries(seed);
    }

    private Map<String, Double> pick(Long other) {
        return null;
    }

    private void remove(Long id) {
    }
}
