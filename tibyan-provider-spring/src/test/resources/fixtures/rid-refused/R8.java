package rid.refused.r8;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), RID-8. The load is a by-primary-key read of the WRONG entity: the
 * service's repository is a repository of Wheel, and the method declares Car. Refuses at condition 8,
 * where the repository's entity is compared to the intent's by KEY (section 4.12.4), never by simple
 * name. This is the anti-mis-bind guard of the new proof: proving a by-primary-key load is not enough
 * on its own, it has to be a load of THIS entity.
 */
@Entity
class Car {
    @Id
    Long id;
    String plate;
}

@Entity
class Wheel {
    @Id
    Long id;
    Integer position;
}

interface WheelJpaRepo extends JpaRepository<Wheel, Long> {
}

@Service
class CarService {

    private final WheelJpaRepo repo;

    public Car getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
