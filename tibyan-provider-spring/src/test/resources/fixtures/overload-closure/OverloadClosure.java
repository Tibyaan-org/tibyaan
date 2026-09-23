package com.example.overload;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/** DL-1.3 fixtures LB-1 to LB-5 (Volume 5 section 4.17.18). Expected labels are in OverloadClosureTest. */
@Entity
class Task {
    @Id
    private Long id;
    private String title;
    public Long getId() { return id; }
    public String getTitle() { return title; }
}

interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByTitle(String title);
}

enum TaskEvent { CREATED, CLOSED }

class TaskCommand {
    private String verb;
}

@Configuration
@ConfigurationProperties(prefix = "tasks")
class TaskConfig {
    private Long defaultOwnerId;
    private boolean strict;
}

@Service
class TaskEngine {
    private final TaskRepository repo;
    TaskEngine(TaskRepository repo) { this.repo = repo; }

    // Two overloads of arity 1: the id overload reads, the entity overload saves.
    public Task process(Long id) { return repo.findById(id).orElse(null); }
    public Task process(Task task) { return repo.save(task); }

    // Two overloads of arity 2: an enum-typed second parameter reads, a command-typed one saves.
    public List<Task> apply(Long id, TaskEvent event) { return repo.findByTitle(event.name()); }
    public List<Task> apply(Long id, TaskCommand command) { repo.save(new Task()); return List.of(); }

    // Two reference overloads of arity 1.
    public String describe(String title) { return title; }
    public String describe(Task task) { repo.save(task); return task.getTitle(); }
}

@Service
public class TaskServiceImpl {

    private final TaskEngine engine;
    private final TaskConfig config;

    public TaskServiceImpl(TaskEngine engine, TaskConfig config) {
        this.engine = engine;
        this.config = config;
    }

    // LB-1: bodiless accessors of declared fields are READ.
    public Long defaultOwner() { return config.getDefaultOwnerId(); }
    public boolean strict() { return config.isStrict(); }

    // LB-2: the parameter's declared type picks the overload: Long reads, Task saves.
    public Task load(Long id) { return engine.process(id); }
    public Task store(Task task) { return engine.process(task); }

    // LB-3: an enum constant picks the enum overload, which reads.
    public List<Task> onCreated(Long id) { return engine.apply(id, TaskEvent.CREATED); }

    // LB-4: a null literal against two reference overloads stays UNKNOWN.
    public String describeNothing() { return engine.describe(null); }

    // LB-5: a method-call argument stays UNKNOWN, naming the argument.
    public String describeLoaded(Long id) { return engine.describe(engine.process(id)); }
}
