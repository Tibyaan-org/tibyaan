package example.helper;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-3. A Spring Data repository of Foo (the framework contract). */
public interface FooRepository extends JpaRepository<Foo, Long> {
}
