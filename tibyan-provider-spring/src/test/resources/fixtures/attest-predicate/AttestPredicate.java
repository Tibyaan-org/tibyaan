package com.example.attest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The AT-1 PREDICATE reference shapes (fixtures ATT-1, ATT-3, ATT-4, ATT-5, ATT-7). Every read here is
 * REFUSED by the structural rules (each is filtered or guarded), so none mints a verified capability; the
 * element tie of each is structurally provable, so the only hole is the PREDICATE hole a category predicate
 * attestation fills. The mutating read exists to prove the read-safety refusal.
 */
@Entity
class Tenant {
    @Id
    Long id;
    String fullName;
    boolean deleted;
}

class TenantView {
}

interface TenantRepository extends JpaRepository<Tenant, Long> {

    List<Tenant> findByFilters(String fullName, Integer pageNum);
}

class TenantMapper {
    TenantView toView(Tenant tenant) {
        return null;
    }
}

class TenantResultSet {
    TenantResultSet(List<Tenant> data) {
    }
}

@Service
class TenantServiceImpl {

    private final TenantRepository tenantRepository;
    private final TenantMapper mapper;

    TenantServiceImpl(TenantRepository tenantRepository, TenantMapper mapper) {
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
    }

    // Filtered list read: the domain parameter fullName refuses it structurally; attestable predicate.
    public TenantResultSet getAllByFilter(String fullName, Integer pageNum) {
        return new TenantResultSet(tenantRepository.findByFilters(fullName, pageNum));
    }

    // Existence-condition by-id read returning a view: the guard refuses it under section 4.2; attestable
    // predicate. Read-safe: the guard reads a field and throws, it never writes.
    public TenantView getActiveTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id).orElseThrow();
        if (tenant.deleted) {
            throw new IllegalStateException("not found");
        }
        return mapper.toView(tenant);
    }

    // A mutating body: a write-set call is visible, so the read-safety check refuses any attestation of it.
    public TenantResultSet getAllByFilterUnsafe(String fullName) {
        tenantRepository.deleteById(1L);
        return new TenantResultSet(tenantRepository.findByFilters(fullName, 0));
    }
}
