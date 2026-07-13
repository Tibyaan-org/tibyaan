package example.support.model;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), EI-1. A clean by-id read of the support Note. Its return type
 * resolves, same-package, to the support Note key, so it backs getSupportNote and never the crm
 * Note. A distinct service simple name keeps the two bindings distinct (validation rule 7).
 */
@Service
public class SupportNoteService {

    public Note getById(Long id) {
        return null;
    }
}
