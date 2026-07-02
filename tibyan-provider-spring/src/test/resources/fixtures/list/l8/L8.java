package com.example.list.l8;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L8: non-framework terminal (the anti-nominal-inference proof). The body loads through a derived
 * query, findByActiveTrue, not the framework findAll. The tool never reads the method name as "list
 * active"; only the framework findAll contract counts, so the read is deferred, not modeled.
 */
@Entity
class Note {
    @Id
    Long id;
    boolean active;
}

interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByActiveTrue();
}

@Service
class NoteService {

    private final NoteRepository repo;

    NoteService(NoteRepository repo) {
        this.repo = repo;
    }

    public List<Note> findAllActive() {
        return repo.findByActiveTrue();
    }
}
