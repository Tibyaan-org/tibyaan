package example.catalog.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface ProductJpaRepository extends JpaRepository<Product, Long> {
}

/**
 * Test fixture (not compiled), EI-2. An entity-returning finder of Product, proving its
 * by-primary-key load (section 4.1.1).
 */
@Service
public class ProductService {

    private final ProductJpaRepository repo;

    ProductService(ProductJpaRepository repo) {
        this.repo = repo;
    }

    public Product getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
