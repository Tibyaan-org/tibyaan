package com.example.e6a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * E6a-2: a transparent NON-generic wrapper whose body is a filtered collection read (the dominant pms
 * shape: a *ResultSet built from selectAllByFilters). The read is made VISIBLE by the body tie (T2, the
 * access is on a Spring Data repository of Lease), then deferred as filtered exactly as 4.10 defers it.
 * Visibility without discovery: this is the case that proves E6a moves no discovery number.
 */
@Entity
class Lease {
    @Id
    Long id;
    String status;
}

class LeaseFilter {
}

class LeaseResultSet {
}

interface LeaseRepository extends JpaRepository<Lease, Long> {

    List<Lease> selectAllByFilters(LeaseFilter filter);
}

class LeaseMapper {
    LeaseResultSet toResultSet(List<Lease> leases) {
        return null;
    }
}

@Service
class LeaseService {

    private final LeaseRepository repo;
    private final LeaseMapper mapper;

    LeaseService(LeaseRepository repo, LeaseMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public LeaseResultSet getLeases(LeaseFilter filter) {
        return mapper.toResultSet(repo.selectAllByFilters(filter));
    }
}
