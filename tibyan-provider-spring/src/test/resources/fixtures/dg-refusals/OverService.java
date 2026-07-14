package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-8b, the helper-resolution mis-bound guard. The helper name is
 * OVERLOADED. The tool will not adjudicate an overloaded or shadowed helper set: it must REFUSE loud
 * rather than read one candidate's body while the id could dispatch to another (which would let a
 * guarded read ship as a plain by-id read).
 */
@Service
public class OverService {

    private final OverRepository overRepository;

    private final RefusalMapper refusalMapper;

    public OverService(OverRepository overRepository, RefusalMapper refusalMapper) {
        this.overRepository = overRepository;
        this.refusalMapper = refusalMapper;
    }

    public OverVTO getById(Long id) {
        Over over = loadOver(id);
        return refusalMapper.toOverVTO(over);
    }

    private Over loadOver(Long id) {
        return overRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
    }

    private Over loadOver(String code) {
        return null;
    }
}
