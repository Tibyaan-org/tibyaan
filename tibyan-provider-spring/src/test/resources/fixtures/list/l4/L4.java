package com.example.list.l4;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * L4: identifier independence. Template has an enum @Id, which maps to no bindable primitive, so its
 * by-id read fails NO_IDENTIFIER; but a clean findAll(Pageable) still yields listTemplate, because a
 * list-all needs no id. Both outcomes coexist on the one entity.
 */
@Entity
class Template {
    @Id
    @Enumerated(EnumType.STRING)
    TemplateType type;
    String subject;
}

enum TemplateType {
    WELCOME,
    RESET
}

interface TemplateRepository extends JpaRepository<Template, TemplateType> {
}

@Service
class TemplateService {

    private final TemplateRepository repo;

    TemplateService(TemplateRepository repo) {
        this.repo = repo;
    }

    public Page<Template> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }
}
