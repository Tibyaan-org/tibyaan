package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-4 variant. The guard sits BEFORE the load. The refusal must name the
 * same defect (a statement beyond the load and its unwrap), proving the wording is position-independent.
 */
@Service
public class WidgetService {

    private final WidgetRepository widgetRepository;

    private final RefusalMapper refusalMapper;

    public WidgetService(WidgetRepository widgetRepository, RefusalMapper refusalMapper) {
        this.widgetRepository = widgetRepository;
        this.refusalMapper = refusalMapper;
    }

    public WidgetVTO getById(Long id) {
        Widget widget = loadWidget(id);
        return refusalMapper.toWidgetVTO(widget);
    }

    private Widget loadWidget(Long id) {
        if (id == null) {
            throw new IllegalStateException("bad id");
        }
        return widgetRepository.findById(id).orElseThrow(() -> new IllegalStateException("nf"));
    }
}
