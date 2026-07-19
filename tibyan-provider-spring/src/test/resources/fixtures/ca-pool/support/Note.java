package ca.pool.support;

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
class SupportNoteService {
    public Note findById(Long id) {
        return null;
    }
}

@RestController
class SupportNoteController {
    private final SupportNoteService supportNoteService;

    SupportNoteController(SupportNoteService supportNoteService) {
        this.supportNoteService = supportNoteService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPORT_NOTE_READ')")
    public Note getNote(@PathVariable Long id) {
        return supportNoteService.findById(id);
    }
}
