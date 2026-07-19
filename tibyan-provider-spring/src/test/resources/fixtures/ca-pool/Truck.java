package ca.pool;

import java.util.List;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PostFilter;

@Entity
class Truck {
    @Id
    Long id;
}

interface TruckRepository extends JpaRepository<Truck, Long> {
}

@Service
class TruckService {
    private final TruckRepository truckRepository;

    TruckService(TruckRepository truckRepository) {
        this.truckRepository = truckRepository;
    }

    public List<Truck> findAll() {
        return truckRepository.findAll();
    }
}

@RestController
class TruckController {
    private final TruckService truckService;

    TruckController(TruckService truckService) {
        this.truckService = truckService;
    }

    @GetMapping
    @PostFilter("filterObject.owner == authentication.name")
    public List<Truck> listTrucks() {
        return truckService.findAll();
    }
}
