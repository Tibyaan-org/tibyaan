package example.crm.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface CrmNoteJpaRepository extends JpaRepository<Note, Long> {
}

/**
 * Test fixture (not compiled), EI-1. A clean by-id read of the crm Note: an entity-returning
 * finder (section 4.1) whose body proves its by-primary-key load (section 4.1.1). Both the return
 * type and the repository's entity argument resolve, same-package, to the crm Note key, so it backs
 * getCrmNote and never the support Note.
 */
@Service
public class CrmNoteService {

    private final CrmNoteJpaRepository repo;

    CrmNoteService(CrmNoteJpaRepository repo) {
        this.repo = repo;
    }

    public Note getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
