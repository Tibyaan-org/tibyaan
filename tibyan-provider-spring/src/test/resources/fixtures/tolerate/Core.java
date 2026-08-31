package com.example.tolerate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Test fixture (not compiled). T1 (the leverage) and T7 (the mis-bound guard): Gizmo has a to-many
 * collection that E5 tolerance skips, and a good findById, so it extracts partial and discovers via
 * rule 4.1. Part is a clean target entity (S2, complete, no partial annotation), and Gizmo's
 * @ManyToOne to it is kept because the target is present (the closure G3 case, in the scan path).
 *
 * <p>The finder loads by primary key and returns the entity untransformed, which section 4.1.1
 * requires of the entity-returning path. T7's point is sharper for it: the binding is exactly right
 * because the load is proven, not merely because the signature fits.
 */
@Entity
class Gizmo {
    @Id
    Long id;
    String name;
    @ManyToOne
    Part mainPart;
    @OneToMany
    Set<Part> parts;
}

@Entity
class Part {
    @Id
    Long id;
    String label;
}

interface GizmoJpaRepository extends JpaRepository<Gizmo, Long> {
}

@Service
class GizmoService {

    private final GizmoJpaRepository repo;

    GizmoService(GizmoJpaRepository repo) {
        this.repo = repo;
    }

    public Gizmo findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
