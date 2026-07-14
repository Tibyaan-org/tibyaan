package example.crm;

import org.springframework.data.jpa.repository.JpaRepository;

/** Test fixture (not compiled), DG-11. A Spring Data repository of the CRM Note. */
public interface CrmNoteRepository extends JpaRepository<Note, Long> {
}
