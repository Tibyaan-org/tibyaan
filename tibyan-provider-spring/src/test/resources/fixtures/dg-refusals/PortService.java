package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-5. The load resolves through a port that has MORE THAN ONE in-tree
 * implementation. It must be REFUSED: the tool never picks an implementation by convention.
 */
@Service
public class PortService {

    private final PortRepository portRepository;

    private final PortMapper portMapper;

    public PortService(PortRepository portRepository, PortMapper portMapper) {
        this.portRepository = portRepository;
        this.portMapper = portMapper;
    }

    public PortVTO getById(Long id) {
        Port port = portRepository.selectById(id).orElseThrow(() -> new IllegalStateException("nf"));
        return portMapper.toPortVTO(port);
    }
}
