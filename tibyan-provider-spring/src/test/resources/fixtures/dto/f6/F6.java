package com.example.dto.f6;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F6: composite assembly (the Building shape). A correct by-primary-key load is followed by a second
 * repository access and view enrichment. Condition 6 refuses: more than one repository access, the
 * deferred pattern P2.
 */
@Entity
class Report {
    @Id
    Long id;
    String title;
}

@Entity
class Line {
    @Id
    Long id;
    String text;
}

interface ReportJpaRepository extends JpaRepository<Report, Long> {
}

interface LineJpaRepository extends JpaRepository<Line, Long> {
}

class ReportMapper {
    ReportVto toVto(Report report) {
        return null;
    }
}

class ReportVto {
    void setLines(List<Line> lines) {
    }
}

@Service
class ReportService {

    private final ReportJpaRepository reportRepo;
    private final LineJpaRepository lineRepo;
    private final ReportMapper mapper;

    ReportService(ReportJpaRepository reportRepo, LineJpaRepository lineRepo, ReportMapper mapper) {
        this.reportRepo = reportRepo;
        this.lineRepo = lineRepo;
        this.mapper = mapper;
    }

    public ReportVto findById(Long id) {
        Report report = reportRepo.findById(id).orElseThrow();
        ReportVto vto = mapper.toVto(report);
        vto.setLines(lineRepo.findAll());
        return vto;
    }
}
