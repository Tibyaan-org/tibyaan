package example.helper;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-3b. An ENTITY-returning read reached through a private helper. The
 * public method returns the entity itself, so section 4.1 discovers it on its signature alone; this
 * fixture is the regression guard that the helper hop never disturbs the entity-returning path.
 */
@Service
public class BarService {

    private final BarRepository barRepository;

    public BarService(BarRepository barRepository) {
        this.barRepository = barRepository;
    }

    public Bar getById(Long id) {
        return loadBar(id);
    }

    private Bar loadBar(Long id) {
        return barRepository.findById(id).orElseThrow(() -> new IllegalStateException("not found"));
    }
}
