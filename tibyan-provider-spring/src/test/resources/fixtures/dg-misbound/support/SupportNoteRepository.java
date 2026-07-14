package example.support;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-11. A Spring Data repository of the SUPPORT Note. */
public interface SupportNoteRepository extends JpaRepository<Note, Long> {
}
