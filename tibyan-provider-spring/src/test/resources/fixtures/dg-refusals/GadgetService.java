package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-4, the most important refusal fixture and the pervasive benchmark
 * case. The private helper carries a POST-load soft-delete guard. The tool cannot tell that guard from
 * an authorization or business filter without interpreting it, so the read must be REFUSED, never
 * emitted as a plain by-id read.
 */
@Service
public class GadgetService {

    private final GadgetRepository gadgetRepository;

    private final RefusalMapper refusalMapper;

    public GadgetService(GadgetRepository gadgetRepository, RefusalMapper refusalMapper) {
        this.gadgetRepository = gadgetRepository;
        this.refusalMapper = refusalMapper;
    }

    public GadgetVTO getById(Long id) {
        Gadget gadget = loadGadget(id);
        return refusalMapper.toGadgetVTO(gadget);
    }

    private Gadget loadGadget(Long id) {
        Gadget gadget = gadgetRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
        if (gadget.getIsDeleted()) {
            throw new IllegalStateException("nf");
        }
        return gadget;
    }
}
