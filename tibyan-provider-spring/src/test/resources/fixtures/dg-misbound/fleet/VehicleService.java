package example.fleet;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled), DG-10. The parent's own read. */
@Service
public class VehicleService {

    public Vehicle getById(Long id) {
        return null;
    }
}
