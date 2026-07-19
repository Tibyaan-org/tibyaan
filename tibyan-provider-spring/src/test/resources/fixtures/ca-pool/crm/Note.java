package ca.pool.crm;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
public class Note {
    @Id
    Long id;
}

@Service
class CrmNoteService {
    public Note findById(Long id) {
        return null;
    }
}

@RestController
class CrmNoteController {
    private final CrmNoteService crmNoteService;

    CrmNoteController(CrmNoteService crmNoteService) {
        this.crmNoteService = crmNoteService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_NOTE_READ')")
    public Note getNote(@PathVariable Long id) {
        return crmNoteService.findById(id);
    }
}
