package example.crm.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface NoteJpaRepository extends JpaRepository<Note, Long> {
}

/**
 * Test fixture (not compiled), EI-6. An entity-returning finder of the crm Note, proving its
 * by-primary-key load (section 4.1.1). Its return type AND its repository's entity argument both
 * resolve, same-package, to the crm Note key. It is the only service in the model; the support Note
 * has no finder, so it must fail loud as NO_BACKING_METHOD rather than mis-bind to this one.
 */
@Service
public class NoteService {

    private final NoteJpaRepository repo;

    NoteService(NoteJpaRepository repo) {
        this.repo = repo;
    }

    public Note getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
