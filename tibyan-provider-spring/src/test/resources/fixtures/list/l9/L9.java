package com.example.list.l9;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L9: mutation in body. The method returns a collection of the entity but also calls a mutation on the
 * repository, so it is a write, not a read. It is refused on the mutation, never bound to listTask.
 */
@Entity
class Task {
    @Id
    Long id;
    String name;
}

interface TaskRepository extends JpaRepository<Task, Long> {
}

@Service
class TaskService {

    private final TaskRepository repo;

    TaskService(TaskRepository repo) {
        this.repo = repo;
    }

    public List<Task> findAll() {
        repo.deleteAll();
        return repo.findAll();
    }
}
