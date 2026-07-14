package example.audit;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-1. A clean entity-returning by-id read of the child. */
@Service
public class NoteService {

    public Note getById(Long id) {
        return null;
    }
}
