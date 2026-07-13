package example.crm.model;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), EI-1. A clean by-id read of the crm Note: an entity-returning
 * finder (section 4.1). Its return type resolves, same-package, to the crm Note key, so it backs
 * getCrmNote and never the support Note.
 */
@Service
public class CrmNoteService {

    public Note getById(Long id) {
        return null;
    }
}
