package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class Status {
    @Id
    Long id;
}

interface StatusJpaRepo extends JpaRepository<Status, Long> {
}

@Service
class StatusService {

    private final StatusJpaRepo repo;
    public Status findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class StatusController {
    private final StatusService statusService;

    StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STATUS_READ')")
    public ResponseEntity<Status> getStatus(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(statusService.findById(id));
    }
}
