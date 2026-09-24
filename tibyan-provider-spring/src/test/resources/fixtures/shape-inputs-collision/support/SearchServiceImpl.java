package example.support.shape;

import org.springframework.stereotype.Service;

import java.util.List;

/** SI-10: one service taking both same-simple-name DTOs, each written qualified where it must be. */
@Service
public class SearchServiceImpl {

    public List<String> crm(example.crm.shape.Filter filter) {
        return List.of();
    }

    public List<String> support(Filter filter) {
        return List.of();
    }
}
