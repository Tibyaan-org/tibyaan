package com.example.dto.f1;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * F1: direct Spring Data load, fused form. The mapping call wraps the unwrapped load directly, and
 * the repository is a Spring Data repository of the entity, so condition 4a holds. Discovers.
 */
@Entity
class Widget {
    @Id
    Long id;
    String name;
}

interface WidgetJpaRepository extends JpaRepository<Widget, Long> {
}

class WidgetMapper {
    WidgetVto toVto(Widget widget) {
        return null;
    }
}

class WidgetVto {
}

@Service
class WidgetService {

    private final WidgetJpaRepository repo;
    private final WidgetMapper mapper;

    WidgetService(WidgetJpaRepository repo, WidgetMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public WidgetVto findById(Long id) {
        return mapper.toVto(repo.findById(id).orElseThrow());
    }
}
