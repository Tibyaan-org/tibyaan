package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

// CA-1: the requested shape. A clean by-id read fronted by a strict @GetMapping anchor whose
// @PreAuthorize is a single hasAuthority literal compiles requiredAuthorities [INVENTORY_READ].
@Entity
class Inventory {
    @Id
    Long id;
}

interface InventoryJpaRepo extends JpaRepository<Inventory, Long> {
}

@Service
class InventoryService {

    private final InventoryJpaRepo repo;
    public Inventory findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class InventoryController {
    private final InventoryService inventoryService;

    InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Inventory getInventory(@PathVariable Long id) {
        return inventoryService.findById(id);
    }
}
