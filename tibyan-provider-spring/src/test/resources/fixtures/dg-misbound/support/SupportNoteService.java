package example.support;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-11. The support context's helper-hop read of its own Note. */
@Service
public class SupportNoteService {

    private final SupportNoteRepository supportNoteRepository;

    private final SupportMapper supportMapper;

    public SupportNoteService(SupportNoteRepository supportNoteRepository, SupportMapper supportMapper) {
        this.supportNoteRepository = supportNoteRepository;
        this.supportMapper = supportMapper;
    }

    public SupportNoteVTO getById(Long id) {
        Note note = loadNote(id);
        return supportMapper.toSupportNoteVTO(note);
    }

    private Note loadNote(Long id) {
        return supportNoteRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
    }
}
