package com.example.dto.f8;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * F8: custom-query terminal (the Tenant shape). The one-hop implementation delegates to a custom
 * @Query method, not a framework by-primary-key read, so the by-primary-key fact lives inside JPQL
 * the tool does not read. Refuses with the deferred pattern P1b near-miss naming the chain.
 */
@Entity
class Doc {
    @Id
    Long id;
    String title;
}

interface DocRepository {
    Optional<Doc> selectById(Long id);
}

interface DocJpaRepository extends JpaRepository<Doc, Long> {
    @Query("SELECT d FROM Doc d JOIN FETCH d.owner WHERE d.id = :id")
    Optional<Doc> findByIdWithRelations(Long id);
}

@Repository
class DocRepositoryImpl implements DocRepository {

    private final DocJpaRepository jpa;

    DocRepositoryImpl(DocJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Doc> selectById(Long id) {
        return jpa.findByIdWithRelations(id);
    }
}

class DocMapper {
    DocVto toVto(Doc doc) {
        return null;
    }
}

class DocVto {
}

@Service
class DocService {

    private final DocRepository repo;
    private final DocMapper mapper;

    DocService(DocRepository repo, DocMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public DocVto findById(Long id) {
        Doc doc = repo.selectById(id).orElseThrow();
        return mapper.toVto(doc);
    }
}
