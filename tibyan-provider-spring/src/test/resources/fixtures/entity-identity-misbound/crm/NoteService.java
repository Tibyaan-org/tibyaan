package example.crm.model;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), EI-6. An entity-returning finder of the crm Note. Its return type
 * resolves, same-package, to the crm Note key. It is the only service in the model; the support
 * Note has no finder, so it must fail loud as NO_BACKING_METHOD rather than mis-bind to this one.
 */
@Service
public class NoteService {

    public Note getById(Long id) {
        return null;
    }
}
