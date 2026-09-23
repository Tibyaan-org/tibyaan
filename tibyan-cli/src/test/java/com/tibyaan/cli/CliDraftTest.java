package com.tibyaan.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code tibyan draft} (spike DL-0) on the unmodified example app: one concrete service, one public method, so
 * exactly one draft, VERIFIED, and the file is byte-identical to the golden copy.
 */
class CliDraftTest {

    private static final Path EXAMPLE_APP = Path.of("..", "examples", "legacy-spring-app");

    @Test
    void draftWritesTheGoldenDraftsFileForTheExampleApp(@TempDir Path dir) throws IOException {
        Path out = dir.resolve("drafts.json");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        int code;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            code = new CommandLine(new TibyanCli()).execute("draft", EXAMPLE_APP.toString(), "--out", out.toString());
        } finally {
            System.setOut(original);
        }

        assertThat(code).isEqualTo(0);
        String stdout = captured.toString(StandardCharsets.UTF_8);
        assertThat(stdout).contains("Drafts: 1 (VERIFIED 1, ATTESTED 0, READ 0, WRITE 0, UNKNOWN 0)");
        assertThat(stdout).contains("Wrote drafts to " + out);

        String actual = Files.readString(out);
        String golden = Files.readString(Path.of("src", "test", "resources", "golden", "draft", "legacy-spring-app.drafts.json"))
                .replace("\r\n", "\n");
        assertThat(actual).isEqualTo(golden);
    }

    @Test
    void draftDefaultsToTheSourceRootDraftsFile(@TempDir Path dir) throws IOException {
        Path app = dir.resolve("app");
        Files.createDirectories(app);
        Files.writeString(app.resolve("NoteService.java"), """
                package com.example.notes;

                import org.springframework.stereotype.Service;

                @Service
                public class NoteService {
                    private final NoteRepository repo;
                    public NoteService(NoteRepository repo) { this.repo = repo; }
                    public void clear() { repo.deleteAll(); }
                }
                """);
        Files.writeString(app.resolve("NoteRepository.java"), """
                package com.example.notes;

                import org.springframework.data.jpa.repository.JpaRepository;

                public interface NoteRepository extends JpaRepository<Note, Long> {
                }
                """);
        Files.writeString(app.resolve("Note.java"), """
                package com.example.notes;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class Note {
                    @Id
                    private Long id;
                }
                """);

        int code = new CommandLine(new TibyanCli()).execute("draft", app.toString());

        assertThat(code).isEqualTo(0);
        String written = Files.readString(app.resolve("tibyan/drafts.json"));
        assertThat(written).contains("\"name\" : \"NoteService_clear\"").contains("\"label\" : \"WRITE\"");
    }

    @Test
    void draftReturnsThreeWhenTheSourceRootIsMissing(@TempDir Path dir) {
        int code = new CommandLine(new TibyanCli()).execute("draft", dir.resolve("missing").toString());
        assertThat(code).isEqualTo(3);
    }
}
