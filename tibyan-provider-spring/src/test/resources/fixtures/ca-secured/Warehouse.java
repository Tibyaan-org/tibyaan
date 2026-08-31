package ca.secured;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.annotation.Secured;
import org.springframework.data.jpa.repository.JpaRepository;

import static ca.secured.Scopes.WAREHOUSE_READ;
import static ca.secured.Scopes.STOCK_READ;

@Entity
class Warehouse {
    @Id
    Long id;
}

interface WarehouseJpaRepo extends JpaRepository<Warehouse, Long> {
}

@Service
class WarehouseService {

    private final WarehouseJpaRepo repo;
    public Warehouse findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

interface WarehouseApi {
    @RequestMapping(method = RequestMethod.GET, value = "/warehouse/{id}")
    ResponseEntity<Warehouse> getWarehouse(@PathVariable("id") Long id);
}

@RestController
class WarehouseController implements WarehouseApi {
    private final WarehouseService warehouseService;

    WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Override
    @Secured({WAREHOUSE_READ, STOCK_READ})
    public ResponseEntity<Warehouse> getWarehouse(Long id) {
        return ResponseEntity.ok(warehouseService.findById(id));
    }
}
