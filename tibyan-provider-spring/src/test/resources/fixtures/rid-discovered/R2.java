package rid.discovered.r2;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Test fixture (not compiled), RID-2. The one-hop delegation (condition 8, path 4b) in the
 * two-statement identity shape (condition 9b): a local initialized from the unwrapped load, then a
 * bare return of that local. This is the shape both real benchmark applications use, so it is the
 * one that had to keep discovering.
 */
@Entity
class Pallet {
    @Id
    Long id;
    String label;
}

interface PalletJpaRepo extends JpaRepository<Pallet, Long> {
}

interface PalletRepository {
    Optional<Pallet> selectById(Long id);
}

@Repository
class PalletRepositoryImpl implements PalletRepository {

    private final PalletJpaRepo jpa;

    @Override
    public Optional<Pallet> selectById(Long id) {
        return jpa.findById(id);
    }
}

@Service
class PalletService {

    private final PalletRepository repository;

    public Pallet getById(Long id) {
        Pallet pallet = repository.selectById(id).orElseThrow();
        return pallet;
    }
}
