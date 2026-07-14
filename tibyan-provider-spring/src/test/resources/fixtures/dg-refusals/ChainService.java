package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-8. The private helper delegates to a FURTHER private method rather
 * than performing the load itself. The trace is one private hop and reads no deeper, so it REFUSES.
 */
@Service
public class ChainService {

    private final ChainRepository chainRepository;

    private final RefusalMapper refusalMapper;

    public ChainService(ChainRepository chainRepository, RefusalMapper refusalMapper) {
        this.chainRepository = chainRepository;
        this.refusalMapper = refusalMapper;
    }

    public ChainVTO getById(Long id) {
        Chain chain = loadChain(id);
        return refusalMapper.toChainVTO(chain);
    }

    private Chain loadChain(Long id) {
        return fetchChain(id);
    }

    private Chain fetchChain(Long id) {
        return chainRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
    }
}
