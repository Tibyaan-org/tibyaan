package example.helper;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-3. The CLEAN private-helper hop: the public read delegates its load
 * to a private same-class helper whose body is exactly the by-primary-key load and its Optional
 * unwrap, with NO guard and no second access. This is the shape the general rule is for.
 */
@Service
public class FooService {

    private final FooRepository fooRepository;

    private final FooMapper fooMapper;

    public FooService(FooRepository fooRepository, FooMapper fooMapper) {
        this.fooRepository = fooRepository;
        this.fooMapper = fooMapper;
    }

    public FooVTO getById(Long id) {
        Foo foo = loadFoo(id);
        return fooMapper.toFooVTO(foo);
    }

    private Foo loadFoo(Long id) {
        return fooRepository.findById(id).orElseThrow(() -> new IllegalStateException("not found"));
    }
}
