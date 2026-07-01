package com.example.modern;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled): a service using syntax that only parses at Java 14 or later, so a
 * parser configured below that level rejects it as if it were malformed (the trial's f1 cause).
 * The read-by-id shape (returns {@code Gadget}, one {@code Long}) still lets the binding discover.
 */
@Service
public class GadgetService {

    public Gadget findById(Long id) {
        // Switch expression: Java 14. Rejected below the JAVA_14 language level.
        String bucket = switch ((int) (id % 2)) {
            case 0 -> "even";
            default -> "odd";
        };
        // instanceof type pattern: Java 16. Also rejected below JAVA_14.
        Object marker = bucket;
        if (marker instanceof String s && !s.isBlank()) {
            return load(id, s);
        }
        return null;
    }

    private Gadget load(Long id, String bucket) {
        // Text block: Java 15.
        String note = """
                gadget %d in the %s bucket
                """.formatted(id, bucket);
        return new Gadget();
    }
}
