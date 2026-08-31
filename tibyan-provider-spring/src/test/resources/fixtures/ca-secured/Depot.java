package ca.secured;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.security.access.annotation.Secured;
import org.springframework.data.jpa.repository.JpaRepository;

import static ca.secured.Scopes.WAREHOUSE_READ;

@Entity
class Depot {
    @Id
    Long id;
}

interface DepotJpaRepo extends JpaRepository<Depot, Long> {
}

@Service
class DepotService {

    private final DepotJpaRepo repo;
    public Depot findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

interface DepotApi {
    @RequestMapping(method = RequestMethod.GET, value = "/depot/{id}")
    ResponseEntity<Depot> getDepot(@PathVariable("id") Long id);
}

@Controller
class DepotController implements DepotApi {
    private final DepotService depotService;

    DepotController(DepotService depotService) {
        this.depotService = depotService;
    }

    @Override
    @Secured(WAREHOUSE_READ)
    public ResponseEntity<Depot> getDepot(Long id) {
        return ResponseEntity.ok(depotService.findById(id));
    }
}
