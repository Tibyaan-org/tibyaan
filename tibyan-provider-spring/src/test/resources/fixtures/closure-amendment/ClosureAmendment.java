package com.example.amendment;

import java.util.List;

import com.google.gson.Gson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** DL-1.2 fixtures LA-1 to LA-6 (Volume 5 section 4.17.16). Expected labels are in ClosureAmendmentTest. */
@Entity
class Note {
    @Id
    private Long id;
    private String body;
    public Long getId() { return id; }
    public String getBody() { return body; }
}

interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByBody(String body);
}

abstract class AbstractNoteConfig {
    private String prefix;
    public String getPrefix() { return prefix; }
    public String getQualifiedPrefix() { return "[" + getPrefix() + "]"; }
}

@Configuration
@ConfigurationProperties(prefix = "notes")
class NoteConfig extends AbstractNoteConfig {
}

interface NoteLookup {
    List<Note> find(String body);
}

@Service
class LocalNoteLookup implements NoteLookup {
    private final NoteRepository repo;
    LocalNoteLookup(NoteRepository repo) { this.repo = repo; }
    public List<Note> find(String body) { return repo.findByBody(body); }
}

@Service
class CachedNoteLookup implements NoteLookup {
    private final NoteRepository repo;
    CachedNoteLookup(NoteRepository repo) { this.repo = repo; }
    public List<Note> find(String body) { return repo.findByBody(body); }
}

interface NoteWriter {
    void write(String body);
}

@Service
class LocalNoteWriter implements NoteWriter {
    private final NoteRepository repo;
    LocalNoteWriter(NoteRepository repo) { this.repo = repo; }
    public void write(String body) { repo.save(new Note()); }
}

@Service
class LoggingNoteWriter implements NoteWriter {
    public void write(String body) { System.out.println(body); }
}

@Service
class Overloaded {
    public String describe(Long id) { return "id"; }
    public String describe(String body) { return "body"; }
}

@Service
public class NoteServiceImpl {

    private final AbstractNoteConfig config;
    private final ObjectProvider<NoteRepository> repoProvider;
    private final Gson gson;
    private final PasswordEncoder encoder;
    private final RestClient restClient;
    private final NoteLookup lookup;
    private final NoteWriter writer;
    private final Overloaded overloaded;

    public NoteServiceImpl(AbstractNoteConfig config, ObjectProvider<NoteRepository> repoProvider, Gson gson,
                           PasswordEncoder encoder, RestClient restClient, NoteLookup lookup, NoteWriter writer,
                           Overloaded overloaded) {
        this.config = config;
        this.repoProvider = repoProvider;
        this.gson = gson;
        this.encoder = encoder;
        this.restClient = restClient;
        this.lookup = lookup;
        this.writer = writer;
        this.overloaded = overloaded;
    }

    // LA-1: a configured subclass of an abstract config; its getter is READ.
    public String prefix() { return config.getQualifiedPrefix(); }

    // LA-2: a provider lookup alone is READ; a save chained on the provided bean is WRITE.
    public Object repo() { return repoProvider.getIfAvailable(); }
    public Note saveViaProvider(Note note) { return repoProvider.getIfAvailable().save(note); }

    // LA-3: Gson and PasswordEncoder are pure.
    public String json(Note note) { return gson.toJson(note); }
    public boolean matches(String raw, String hash) { return encoder.matches(raw, hash); }

    // LA-4: a fluent GET is READ with the call named; a fluent POST is WRITE.
    public String fetch(String id) { return restClient.get().uri("/notes/" + id).retrieve().body(String.class); }
    public String post(String body) { return restClient.post().uri("/notes").body(body).retrieve().body(String.class); }

    // LA-5: two implementations that both read give READ; two where one writes give WRITE.
    public List<Note> find(String body) { return lookup.find(body); }
    public String write(String body) { writer.write(body); return body; }

    // LA-6: two same-arity overloads stay UNKNOWN.
    public String describe(Long id) { return overloaded.describe(id); }
}
