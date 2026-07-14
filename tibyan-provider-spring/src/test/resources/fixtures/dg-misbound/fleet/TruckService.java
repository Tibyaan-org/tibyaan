package example.fleet;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-10. The sibling's own read. */
@Service
public class TruckService {

    public Truck getById(Long id) {
        return null;
    }
}
