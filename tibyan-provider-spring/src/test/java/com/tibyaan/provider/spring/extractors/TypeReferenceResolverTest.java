package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-stage key resolver (Volume 5 section 4.12.4), tested at the reference-resolution level with
 * the pre-registered fixtures that exercise scoping directly: EI-4 (single-type import), EI-5
 * (unqualified collision refuses), EI-11 (same-package), EI-12 (wildcard-only collision refuses).
 * All names are synthetic (Volume 5 section 4.12; private-benchmark redaction).
 */
class TypeReferenceResolverTest {

    private static final String CRM_NOTE = "example.crm.model.Note";
    private static final String SUPPORT_NOTE = "example.support.model.Note";

    // Two distinct entities sharing the simple name Note, in different packages: the collision case.
    private static CompilationUnit crmNote() {
        return StaticJavaParser.parse(
                "package example.crm.model; import javax.persistence.Entity; @Entity public class Note {}");
    }

    private static CompilationUnit supportNote() {
        return StaticJavaParser.parse(
                "package example.support.model; import javax.persistence.Entity; @Entity public class Note {}");
    }

    @Test
    void ei4_singleTypeImportResolvesToTheImportedEntity() {
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.other; import example.crm.model.Note; "
                        + "public class Ticket { private Note note; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(
                List.of(crmNote(), supportNote(), reference));

        TypeReferenceResolver.Resolution resolution = resolver.resolve("Note", reference);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.fqcn()).isEqualTo(CRM_NOTE);
        assertThat(resolution.kind()).isEqualTo(TypeReferenceResolver.Kind.ENTITY);
    }

    @Test
    void ei5_unqualifiedCollidingReferenceRefusesNamingTheCandidates() {
        // The reference is in a third package with no import that names either Note; scoping cannot
        // narrow it, so it refuses loud rather than picking one of two same-named entities.
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.other; public class Ticket { private Note note; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(
                List.of(crmNote(), supportNote(), reference));

        TypeReferenceResolver.Resolution resolution = resolver.resolve("Note", reference);

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.candidates()).containsExactlyInAnyOrder(CRM_NOTE, SUPPORT_NOTE);
    }

    @Test
    void ei11_samePackageReferenceResolvesWithoutAnImport() {
        // The reference sits in the crm package with no import; same-package scoping (resolver step b)
        // binds it to the crm Note, not the support Note.
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.crm.model; public class Ticket { private Note note; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(
                List.of(crmNote(), supportNote(), reference));

        TypeReferenceResolver.Resolution resolution = resolver.resolve("Note", reference);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.fqcn()).isEqualTo(CRM_NOTE);
    }

    @Test
    void ei12_wildcardReachingTwoEntitiesRefuses() {
        // Two wildcard imports each reach an in-tree Note; the resolver cannot narrow to one and
        // refuses (resolver step d), rather than guessing.
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.other; import example.crm.model.*; import example.support.model.*; "
                        + "public class Ticket { private Note note; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(
                List.of(crmNote(), supportNote(), reference));

        TypeReferenceResolver.Resolution resolution = resolver.resolve("Note", reference);

        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.candidates()).containsExactlyInAnyOrder(CRM_NOTE, SUPPORT_NOTE);
    }

    @Test
    void uniqueSimpleNameResolvesByTheFastPathWithoutImports() {
        // The no-regression clause (section 4.12.3, condition 1): a unique simple name resolves to its
        // one declaration with no import consulted, which is what keeps a no-collision app identical.
        CompilationUnit product = StaticJavaParser.parse(
                "package example.catalog; import javax.persistence.Entity; @Entity public class Product {}");
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.other; public class Cart { private Product product; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(List.of(product, reference));

        TypeReferenceResolver.Resolution resolution = resolver.resolve("Product", reference);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.fqcn()).isEqualTo("example.catalog.Product");
        assertThat(resolution.kind()).isEqualTo(TypeReferenceResolver.Kind.ENTITY);
    }

    @Test
    void aLibraryTypeIsNotInTree() {
        CompilationUnit reference = StaticJavaParser.parse(
                "package example.other; public class Cart { private String label; }");
        TypeReferenceResolver resolver = TypeReferenceResolver.from(List.of(reference));

        assertThat(resolver.resolve("String", reference).isNotInTree()).isTrue();
    }
}
