package example.audit;

import org.springframework.stereotype.Service;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-1. A clean entity-returning by-id read of the child. */
interface NoteJpaRepo extends JpaRepository<Note, Long> {
}

@Service
public class NoteService {

    private final NoteJpaRepo repo;

    public Note getById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
