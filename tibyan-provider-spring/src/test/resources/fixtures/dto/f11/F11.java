package com.example.dto.f11;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * F11: ambiguity across kinds. One entity-returning method and one view-returning method both back
 * the same entity, with no precedence between the two kinds. Condition 10 refuses as
 * AMBIGUOUS_BINDING, listing both candidates.
 */
@Entity
class Job {
    @Id
    Long id;
    String title;
}

interface JobJpaRepository extends JpaRepository<Job, Long> {
}

class JobMapper {
    JobVto toVto(Job job) {
        return null;
    }
}

class JobVto {
}

@Service
class JobService {

    private final JobJpaRepository repo;
    private final JobMapper mapper;

    JobService(JobJpaRepository repo, JobMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public Job getEntity(Long id) {
        return repo.findById(id).orElseThrow();
    }

    public JobVto findById(Long id) {
        return mapper.toVto(repo.findById(id).orElseThrow());
    }
}
