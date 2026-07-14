package example.helper;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-3b. A Spring Data repository of Bar. */
public interface BarRepository extends JpaRepository<Bar, Long> {
}
