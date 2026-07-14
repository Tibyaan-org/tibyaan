package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled). The second-access disqualifier carried through the private hop: the
 * helper performs two persistence accesses. It REFUSES.
 */
@Service
public class SecondService {

    private final SecondRepository secondRepository;

    private final RefusalMapper refusalMapper;

    public SecondService(SecondRepository secondRepository, RefusalMapper refusalMapper) {
        this.secondRepository = secondRepository;
        this.refusalMapper = refusalMapper;
    }

    public SecondVTO getById(Long id) {
        Second second = loadSecond(id);
        return refusalMapper.toSecondVTO(second);
    }

    private Second loadSecond(Long id) {
        Second second = secondRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
        secondRepository.findById(id);
        return second;
    }
}
