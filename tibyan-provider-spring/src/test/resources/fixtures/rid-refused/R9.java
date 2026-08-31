package rid.refused.r9;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-9. Two identity-shape violations, one per service, both loading
 * cleanly by primary key and both refusing at condition 9.
 *
 * <p>{@code NodeService} navigates off the loaded entity, so what it returns is a DIFFERENT instance
 * of the same type: the capability would say "get Node by id" and hand back that node's parent.
 * {@code BadgeService} guards after the load, which is the pervasive soft-delete shape; the guard is
 * a semantic the structure cannot read, so the read is refused rather than silently stripped of it.
 */
@Entity
class Node {
    @Id
    Long id;
    Node parent;
}

interface NodeJpaRepo extends JpaRepository<Node, Long> {
}

@Service
class NodeService {

    private final NodeJpaRepo repo;

    public Node getById(Long id) {
        return repo.findById(id).orElseThrow().getParent();
    }
}

@Entity
class Badge {
    @Id
    Long id;
    Boolean deleted;
}

interface BadgeJpaRepo extends JpaRepository<Badge, Long> {
}

@Service
class BadgeService {

    private final BadgeJpaRepo repo;

    public Badge getById(Long id) {
        Badge badge = repo.findById(id).orElseThrow();
        if (badge.deleted) {
            throw new IllegalStateException("deleted");
        }
        return badge;
    }
}
