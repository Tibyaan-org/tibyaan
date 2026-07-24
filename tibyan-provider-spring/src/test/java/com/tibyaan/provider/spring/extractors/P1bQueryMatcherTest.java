package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The P1b strict by-primary-key custom-query matcher, pre-registered fixtures P1b-1..P1b-10 (Volume 5,
 * section 4.2.10.6). Each case is a single repository method: the matcher reads its {@code @Query} as a
 * bounded token subset and either VERIFIES a nothing-but-a-by-pk load, REFUSES with a located reason, or
 * reports NOT_A_QUERY so the caller keeps its framework-read message.
 *
 * <p>The refusal is the rule's primary path (section 4.2.10.1): the defining case, P1b-4, is a filtered
 * WHERE, and P1b-5 is the inner JOIN FETCH that is exactly why pms Tenant fails. The matcher is exercised
 * directly here; its threading into the section 4.2 read discovery is covered end to end by the scan test.
 */
class P1bQueryMatcherTest {

    private static final String ENTITY = "Tenant";
    private static final String ID_FIELD = "id";

    private static MethodDeclaration method(String source) {
        return StaticJavaParser.parseMethodDeclaration(source);
    }

    private static P1bQueryMatcher.Verdict evaluate(String source) {
        return P1bQueryMatcher.evaluate(method(source), ENTITY, ID_FIELD);
    }

    // ---------- Verified: nothing but a by-primary-key load ----------

    @Test
    void p1b1_cleanByPkSelectWithNamedParameter_isVerified() {
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.id = :id\") "
                        + "Optional<Tenant> findByIdWithRelations(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.VERIFIED);
    }

    @Test
    void p1b2_cleanByPkSelectWithPositionalParameter_isVerified() {
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.id = ?1\") Optional<Tenant> load(Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.VERIFIED);
    }

    @Test
    void p1b3_leftJoinFetchOnlyIsStillAByPkLoad_isVerified() {
        // LEFT JOIN FETCH never drops the root when the association is absent, so the eager fetch does not
        // change which row the id selects. The value = "..." form is read the same as the single-member.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(value = \"SELECT t FROM Tenant t "
                        + "LEFT JOIN FETCH t.user LEFT JOIN FETCH t.unit u WHERE t.id = :id\") "
                        + "Optional<Tenant> findByIdWithRelations(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.VERIFIED);
    }

    // ---------- Refusals: present but not provably nothing-but-a-load ----------

    @Test
    void p1b4_aFilteredWhereIsRefused_theDefiningCase() {
        // The semantic-lie guard (P5): a second predicate makes this a filtered read, never a by-id load.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.id = :id AND t.status = 'ACTIVE'\") "
                        + "Optional<Tenant> findActiveById(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("filters beyond the primary key").contains("P1b, P5");
    }

    @Test
    void p1b5_anInnerJoinFetchIsAnExistenceFilter_isRefused() {
        // This is exactly why pms Tenant fails: an inner JOIN FETCH on a nullable association drops the
        // userless rows, so the query is not a faithful by-id load.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t JOIN FETCH t.user WHERE t.id = :id\") "
                        + "Optional<Tenant> findByIdWithRelations(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("inner JOIN FETCH").contains("P1b, P4");
    }

    @Test
    void p1b6_aProjectionIsNotALoadOfTheEntity_isRefused() {
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT new com.acme.TenantDto(t.id, t.name) FROM Tenant t WHERE t.id = :id\") "
                        + "TenantDto findDtoById(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("P1b, P3");
    }

    @Test
    void p1b7_aNativeQueryIsNotTiedToTheEntity_isRefused() {
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(value = \"SELECT * FROM tenant WHERE id = :id\", nativeQuery = true) "
                        + "Optional<Tenant> findByIdNative(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("native @Query").contains("P1b, P2");
    }

    @Test
    void p1b8_anUnlocatableQueryTextIsRefused_neverAssumed() {
        // The @Query value is a constant reference, not a readable literal: unlocatable, so refused.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(TenantQueries.BY_ID) Optional<Tenant> findByIdWithRelations(@Param(\"id\") Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("not a readable literal").contains("P1b, P2");
    }

    @Test
    void p1b9_aWhereOnADifferentFieldIsNotAByPkLoad_isRefused() {
        // A single, unfiltered equality, but on a non-id field: a lookup, not a by-primary-key load.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.code = :id\") "
                        + "Optional<Tenant> findByCode(@Param(\"id\") String id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("not the entity's id field").contains("P1b, P5");
    }

    @Test
    void p1b10_aMethodWithoutAtQueryIsNotAQuery_soTheCallerKeepsItsMessage() {
        P1bQueryMatcher.Verdict verdict = evaluate("Optional<Tenant> findBySomething(Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.NOT_A_QUERY);
        assertThat(verdict.reason()).isNull();
    }

    // ---------- Two further guards on the id-equality binding ----------

    @Test
    void aQueryWithMoreThanOneParameterIsNotAByIdLoad_isRefused() {
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.id = :id\") "
                        + "Optional<Tenant> find(@Param(\"id\") Long id, String tenant);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("more than one parameter").contains("P1b, condition 2");
    }

    @Test
    void aNamedBindNotMatchingTheSingleParameterIsRefused() {
        // The WHERE binds :ident, but the method's single parameter is neither named ident nor @Param'd so.
        P1bQueryMatcher.Verdict verdict = evaluate(
                "@Query(\"SELECT t FROM Tenant t WHERE t.id = :ident\") Optional<Tenant> load(Long id);");

        assertThat(verdict.kind()).isEqualTo(P1bQueryMatcher.Kind.REFUSED);
        assertThat(verdict.reason()).contains("not its single id parameter").contains("P1b, P6");
    }
}
