package ca.preauth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.jpa.repository.JpaRepository;

// CA-2: hasAnyAuthority compiles the two-element any-of sequence, in order.
@Entity
class Report {
    @Id
    Long id;
}

interface ReportJpaRepo extends JpaRepository<Report, Long> {
}

@Service
class ReportService {

    private final ReportJpaRepo repo;
    public Report findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

@RestController
class ReportController {
    private final ReportService reportService;

    ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('A_READ', 'B_READ')")
    public Report getReport(@PathVariable Long id) {
        return reportService.findById(id);
    }
}
