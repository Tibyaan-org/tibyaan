package com.example.enumcollision.support;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EI-9, the MIS-CONSTRAINED guard, support half. The same simple name, Priority, with different constants.
 * This read must carry URGENT and ROUTINE, never the crm context's LOW and HIGH.
 */
@Entity
class SupportNote {
    @Id
    Long id;
    String body;
}

enum Priority {
    URGENT,
    ROUTINE
}

interface SupportNoteRepository extends JpaRepository<SupportNote, Long> {

    List<SupportNote> findByPriority(Priority priority);
}

class SupportNoteResultSet {
    SupportNoteResultSet(List<SupportNote> data) {
    }
}

@Service
class SupportNoteServiceImpl {

    private final SupportNoteRepository repository;

    SupportNoteServiceImpl(SupportNoteRepository repository) {
        this.repository = repository;
    }

    public SupportNoteResultSet byPriority(Priority priority) {
        return new SupportNoteResultSet(repository.findByPriority(priority));
    }
}
