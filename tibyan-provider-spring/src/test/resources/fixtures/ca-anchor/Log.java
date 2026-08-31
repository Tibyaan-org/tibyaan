package ca.anchor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
class Log {
    @Id
    Long id;
}

interface LogJpaRepo extends JpaRepository<Log, Long> {
}

@Service
class LogService {

    private final LogJpaRepo repo;
    public Log findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class LogController {
    private final LogService logService;

    LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LOG_READ')")
    public Log getLog(@PathVariable Long id) {
        System.out.println("reading " + id);
        return logService.findById(id);
    }
}
