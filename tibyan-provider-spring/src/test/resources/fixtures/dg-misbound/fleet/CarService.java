package example.fleet;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-10. The child's own read. It must bind HERE, never to the parent's
 * or the sibling's method, even though all three take the same inherited id type. */
@Service
public class CarService {

    public Car getById(Long id) {
        return null;
    }
}
