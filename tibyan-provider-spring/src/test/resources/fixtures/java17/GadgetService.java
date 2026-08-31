package com.example.modern;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface GadgetJpaRepository extends JpaRepository<Gadget, Long> {
}

/**
 * Test fixture (not compiled): a service using syntax that only parses at Java 14 or later, so a
 * parser configured below that level rejects it as if it were malformed (the trial's f1 cause).
 * The read-by-id shape (returns {@code Gadget}, one {@code Long}) still lets the binding discover.
 *
 * <p>The modern syntax sits in {@code describe}, a non-matching sibling, because section 4.1.1
 * requires the matching method's own body to be nothing but the by-primary-key load and its return.
 * The file still fails to parse below Java 14, which is the whole point of the fixture.
 */
@Service
public class GadgetService {

    private final GadgetJpaRepository repo;

    GadgetService(GadgetJpaRepository repo) {
        this.repo = repo;
    }

    public Gadget findById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    // Decoy, and the carrier of the post-Java-13 syntax this fixture exists to parse.
    public String describe(Long id) {
        // Switch expression: Java 14. Rejected below the JAVA_14 language level.
        String bucket = switch ((int) (id % 2)) {
            case 0 -> "even";
            default -> "odd";
        };
        // instanceof type pattern: Java 16. Also rejected below JAVA_14.
        Object marker = bucket;
        if (marker instanceof String s && !s.isBlank()) {
            // Text block: Java 15.
            return """
                    gadget %d in the %s bucket
                    """.formatted(id, s);
        }
        return bucket;
    }
}
