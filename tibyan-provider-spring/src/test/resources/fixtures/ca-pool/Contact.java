package ca.pool;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@Entity
class Contact {
    @Id
    Long id;
    boolean active;
}

interface ContactRepository extends JpaRepository<Contact, Long> {
}

@Service
class ContactService {
    private final ContactRepository contactRepository;

    ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public List<Contact> findAll() {
        return contactRepository.findAll();
    }

    public List<Contact> findCompleted() {
        return contactRepository.findAll().stream().filter(c -> c.active).collect(Collectors.toList());
    }
}

@RestController
class ContactController {
    private final ContactService contactService;

    ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTACT_LIST')")
    public List<Contact> listContacts() {
        return contactService.findAll();
    }

    @GetMapping("/completed")
    @PreAuthorize("hasAuthority('IGNORED_NON_POOL')")
    public List<Contact> completed() {
        return contactService.findCompleted();
    }
}
