package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled). The mutation disqualifier carried through the private hop: the helper
 * writes. A read binds to no write, so it REFUSES.
 */
@Service
public class MutService {

    private final MutRepository mutRepository;

    private final RefusalMapper refusalMapper;

    public MutService(MutRepository mutRepository, RefusalMapper refusalMapper) {
        this.mutRepository = mutRepository;
        this.refusalMapper = refusalMapper;
    }

    public MutVTO getById(Long id) {
        Mut mut = loadMut(id);
        return refusalMapper.toMutVTO(mut);
    }

    private Mut loadMut(Long id) {
        Mut mut = mutRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
        mutRepository.save(mut);
        return mut;
    }
}
