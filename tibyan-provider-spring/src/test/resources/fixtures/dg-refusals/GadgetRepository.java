package example.refuse;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled). A Spring Data repository of Gadget. */
public interface GadgetRepository extends JpaRepository<Gadget, Long> {
}
