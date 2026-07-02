package com.example.list.l11;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L11: the list mis-bound guard. Book has exactly one clean findAll on its own repository; a sibling
 * entity Author has its own. listBook must bind to exactly BookService#findAll and its element must be
 * exactly Book, never the sibling's collection.
 */
@Entity
class Book {
    @Id
    Long id;
    String title;
}

@Entity
class Author {
    @Id
    Long id;
    String name;
}

interface BookRepository extends JpaRepository<Book, Long> {
}

interface AuthorRepository extends JpaRepository<Author, Long> {
}

@Service
class BookService {

    private final BookRepository repo;

    BookService(BookRepository repo) {
        this.repo = repo;
    }

    public List<Book> findAll() {
        return repo.findAll();
    }
}

@Service
class AuthorService {

    private final AuthorRepository repo;

    AuthorService(AuthorRepository repo) {
        this.repo = repo;
    }

    public List<Author> findAll() {
        return repo.findAll();
    }
}
