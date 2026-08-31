package rid.refused.r4;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Test fixture (not compiled), RID-4. THE MIS-BIND, the live instance section 4.1.1 was written for,
 * reduced to its structure. The signature satisfies section 4.1 conditions 1 to 4 exactly: a public
 * {@code @Service} method returning the entity and taking one Long. The body treats that Long as a
 * FOREIGN key, runs a filter query, and returns the first hit.
 *
 * <p>Before section 4.1.1 this bound as {@code getCustomerProduct(id)}, so an agent passing the
 * entity's primary key got a different row or an exception. Now it refuses at condition 8: a derived
 * finder that is not a framework by-primary-key read and carries no provable by-pk {@code @Query} is
 * not evidence of a by-primary-key load.
 */
@Entity
class CustomerProduct {
    @Id
    Long id;
    Long productId;
}

interface CustomerProductJpaRepo extends JpaRepository<CustomerProduct, Long> {
    List<CustomerProduct> findAllByProductId(Long productId);
}

@Service
class CustomerProductService {

    private final CustomerProductJpaRepo repo;

    public CustomerProduct validateExistence(Long id) {
        List<CustomerProduct> found = repo.findAllByProductId(id);
        return found.get(0);
    }
}
