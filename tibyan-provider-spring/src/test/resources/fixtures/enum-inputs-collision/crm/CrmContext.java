package com.example.enumcollision.crm;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EI-9, the MIS-CONSTRAINED guard, crm half. This context declares an enum named Priority whose constants
 * are LOW and HIGH. The support context declares a DIFFERENT Priority (URGENT, ROUTINE). The tool resolves
 * the parameter's type by key (Volume 5 section 4.12.4), so this read must carry LOW and HIGH. Carrying the
 * other context's set would advertise values this application rejects, which is the breach.
 */
@Entity
class CrmNote {
    @Id
    Long id;
    String body;
}

enum Priority {
    LOW,
    HIGH
}

interface CrmNoteRepository extends JpaRepository<CrmNote, Long> {

    List<CrmNote> findByPriority(Priority priority);
}

class CrmNoteResultSet {
    CrmNoteResultSet(List<CrmNote> data) {
    }
}

@Service
class CrmNoteServiceImpl {

    private final CrmNoteRepository repository;

    CrmNoteServiceImpl(CrmNoteRepository repository) {
        this.repository = repository;
    }

    public CrmNoteResultSet byPriority(Priority priority) {
        return new CrmNoteResultSet(repository.findByPriority(priority));
    }
}
