package example.crm;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-11, the helper-hop mis-bound guard. A clean private-helper hop over
 * the CRM Note, while another bounded context declares a DIFFERENT entity of the same simple name. The
 * load repository and the helper's return type must both resolve to THIS context's entity by key, so
 * this read can never bind the other context's Note.
 */
@Service
public class CrmNoteService {

    private final CrmNoteRepository crmNoteRepository;

    private final CrmMapper crmMapper;

    public CrmNoteService(CrmNoteRepository crmNoteRepository, CrmMapper crmMapper) {
        this.crmNoteRepository = crmNoteRepository;
        this.crmMapper = crmMapper;
    }

    public CrmNoteVTO getById(Long id) {
        Note note = loadNote(id);
        return crmMapper.toCrmNoteVTO(note);
    }

    private Note loadNote(Long id) {
        return crmNoteRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
    }
}
