package example.support.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface SupportNoteJpaRepository extends JpaRepository<Note, Long> {
}

/**
 * Test fixture (not compiled), EI-1. A clean by-id read of the support Note, proving its
 * by-primary-key load (section 4.1.1). Its return type and its repository both resolve, same-package,
 * to the support Note key, so it backs getSupportNote and never the crm Note. A distinct service
 * simple name kept the two bindings distinct under rule 7 before Binding Identity qualified them (Volume 7 section 12).
 */
@Service
public class SupportNoteService {

    private final SupportNoteJpaRepository repo;

    SupportNoteService(SupportNoteJpaRepository repo) {
        this.repo = repo;
    }

    public Note getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
