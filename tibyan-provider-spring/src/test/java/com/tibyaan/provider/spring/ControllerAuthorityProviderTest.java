package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase Controller-Authority, the pre-registered fixtures (Volume 5, section 4.14.7, CA-1 to CA-18).
 * These fixtures live in test resources only and are never compiled; the provider reads them as source,
 * exactly as it reads a real app. All names are synthetic.
 *
 * <p>The phase decorates a discovered capability with the declared security contract its controller
 * surface carries: a COMPILED any-of authority sequence, a SECURITY-UNVERIFIABLE marker with a closed
 * reason, or nothing (ABSENT). It grants no discovery and mints no capability. The most important
 * fixtures are the security honesty boundary (CA-4, CA-5, CA-6, CA-6b, CA-10, CA-14: everything outside
 * the parseable set becomes unverifiable, never a partial authority list) and the cross-source mis-bound
 * guards (CA-11: no read ever carries another read's authorities).
 */
class ControllerAuthorityProviderTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- @PreAuthorize: the compilation set and the absence case ----------

    @Test
    void preauth_compilesTheParseableSetAndLeavesAbsenceDistinct() {
        CapabilityModel model = provider.scan(input("ca-preauth")).model();

        // CA-1: hasAuthority compiles a one-element any-of sequence.
        assertThat(requiredAuthorities(model, "getInventory")).containsExactly("INVENTORY_READ");
        // CA-2: hasAnyAuthority compiles the multi-element sequence in order.
        assertThat(requiredAuthorities(model, "getReport")).containsExactly("A_READ", "B_READ");
        // CA-3: a class-level @PreAuthorize applies to the handler.
        assertThat(requiredAuthorities(model, "getLedger")).containsExactly("LEDGER_READ");
        // CA-3b: the method-level declaration wins over the class-level one, alone.
        assertThat(requiredAuthorities(model, "getAudit")).containsExactly("METHOD_LEVEL");

        // CA-7: absence renders distinctly from unverifiable: NEITHER annotation is present.
        Capability plain = named(model, "getPlain");
        assertThat(requiredAuthoritiesRaw(plain)).isEmpty();
        assertThat(securityUnverifiable(plain)).isEmpty();

        // CA-10: the name-trap. hasAuthority(AUTH_READ) is a SpEL variable, never resolved, even though an
        // in-tree constant AUTH_READ exists: unverifiable, unparseable, with NO authority list.
        assertThat(securityUnverifiable(named(model, "getTrap"))).contains("unparseable-expression");
        assertThat(requiredAuthoritiesRaw(named(model, "getTrap"))).isEmpty();

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    // ---------- the security honesty boundary: everything outside the parseable set (CA-4 to CA-14) ----

    @Test
    void refusals_everythingOutsideTheParseableSetIsUnverifiableNeverPartial() {
        CapabilityModel model = provider.scan(input("ca-refusals")).model();

        // CA-4, CA-5, CA-6: hasPermission, a custom bean, and a compound (both and/or) are unparseable.
        assertUnverifiable(model, "getPerm", "unparseable-expression");
        assertUnverifiable(model, "getBean", "unparseable-expression");
        assertUnverifiable(model, "getCompound", "unparseable-expression");
        assertUnverifiable(model, "getMixed", "unparseable-expression");
        // CA-6b: hasRole and @RolesAllowed are role expressions; NO ROLE_ prefix is ever emitted.
        assertUnverifiable(model, "getRole", "role-expression");
        assertUnverifiable(model, "getAllowed", "role-expression");
        // CA-14 (by-id): @PostAuthorize is a result annotation.
        assertUnverifiable(model, "getPost", "result-annotation");

        // Every one is emitted (not dropped) and carries NO authority list: never partial.
        for (String capability : List.of("getPerm", "getBean", "getCompound", "getMixed", "getRole",
                "getAllowed", "getPost")) {
            assertThat(requiredAuthoritiesRaw(named(model, capability)))
                    .as(capability + " must carry no partial authority list")
                    .isEmpty();
        }
        assertThat(validator.validate(model).isValid()).isTrue();
    }

    // ---------- @Secured: the census shape, the gate, and the constant rule (CA-12, CA-17) ----------

    @Test
    void secured_compilesTheCensusShapeUnderTheGateAndConstantRule() {
        CapabilityModel model = provider.scan(input("ca-secured")).model();

        // CA-12: the split idiom (mapping and @PathVariable on the interface, body and @Secured on the
        // impl), static-imported constants resolving to numeric literals, under an in-tree modern gate.
        assertThat(requiredAuthorities(model, "getWarehouse")).containsExactly("480012", "480013");
        // CA-17: the same, where the concrete class is @Controller (not @RestController) returning
        // ResponseEntity, and the body is ResponseEntity.ok(...). Anchors and compiles.
        assertThat(requiredAuthorities(model, "getDepot")).containsExactly("480012");
        // CA-12b: a constant whose initializer is a concatenation, not a single literal, does not resolve.
        assertUnverifiable(model, "getBadConst", "unresolvable-constant");

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    @Test
    void secured_withoutTheModernGateIsUnverifiable() {
        // CA-12c: no in-tree @EnableMethodSecurity(securedEnabled = true); a legacy
        // @EnableGlobalMethodSecurity is present instead, so @Secured is not taken as literal authorities.
        CapabilityModel model = provider.scan(input("ca-nogate")).model();
        assertUnverifiable(model, "getLegacy", "no-modern-enablement");
    }

    // ---------- pooling, list decoration, and the cross-source mis-bound guard (CA-9, CA-11, CA-16) -----

    @Test
    void pooling_oneCapabilityPerReadDecoratedAndNoOrphanAuthorities() {
        CapabilityModel model = provider.scan(input("ca-pool")).model();

        // CA-9: a read reachable via a strict controller anchor and discovered from the service is ONE
        // capability, decorated. Not two tools for one read.
        assertThat(model.capabilities()).extracting(Capability::name).filteredOn("getWidget"::equals)
                .as("exactly one getWidget capability (pooled, not duplicated)").hasSize(1);
        assertThat(requiredAuthorities(model, "getWidget")).containsExactly("WIDGET_READ");

        // CA-9b: a controller delegating to a service read the rules REFUSE (a soft-delete-guarded view
        // read) mints no capability, and its authority attaches to NOTHING (no orphan authorities).
        assertThat(model.capabilities()).extracting(Capability::name).doesNotContain("getGhost");
        assertNoCapabilityCarries(model, "GHOST_READ");

        // CA-16: list pooling. A no-arg list anchor decorates listContact; a paged anchor decorates
        // listOrderLine; a @PostFilter list is result-annotation; a handler delegating to a non-pool
        // method (findCompleted) anchors nothing, so its authority attaches nowhere.
        assertThat(requiredAuthorities(model, "listContact")).containsExactly("CONTACT_LIST");
        assertThat(requiredAuthorities(model, "listOrderLine")).containsExactly("ORDER_LIST");
        assertUnverifiable(model, "listTruck", "result-annotation");
        assertNoCapabilityCarries(model, "IGNORED_NON_POOL");

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    @Test
    void ca11_noReadEverCarriesAnotherReadsAuthorities() {
        // The cross-source mis-bound guard, over two same-simple-named entities across bounded contexts:
        // each read binds its own service method resolved by key, and each carries exactly its own
        // anchor's authority, never the sibling's. A wrong authority is a mis-bind, full stop.
        CapabilityModel model = provider.scan(input("ca-pool")).model();

        assertThat(requiredAuthorities(model, "getCrmNote")).containsExactly("CRM_NOTE_READ");
        assertThat(requiredAuthorities(model, "getSupportNote")).containsExactly("SUPPORT_NOTE_READ");
        assertThat(requiredAuthorities(model, "getCrmNote")).doesNotContain("SUPPORT_NOTE_READ");
        assertThat(requiredAuthorities(model, "getSupportNote")).doesNotContain("CRM_NOTE_READ");
    }

    // ---------- the E6b filtered refusal, conflicts, and anchor-shape refusals (CA-8, CA-13, CA-18) -----

    @Test
    void anchorShapes_filteredRefusalConflictsAndNonStrictBodiesAreHandled() {
        CapabilityModel model = provider.scan(input("ca-anchor")).model();

        // CA-8: a @RequestParam GET carrying an authority refuses as filtered and anchors nothing; the
        // clean by-id GET has no security, so getFiltered stays ABSENT and the filter authority orphans.
        assertAbsent(model, "getFiltered");
        assertNoCapabilityCarries(model, "SEARCH_AUTH");

        // CA-13: two anchors with different sequences; a declared anchor plus an undeclared route.
        assertUnverifiable(model, "getConf", "conflicting-declarations");
        assertUnverifiable(model, "getConf2", "conflicting-declarations");

        // CA-18: a two-statement body, a status().body() envelope, and a nested second call each fail the
        // strict-anchor conditions; with a declaration present the composition yields conflicting.
        assertUnverifiable(model, "getLog", "conflicting-declarations");
        assertUnverifiable(model, "getStatus", "conflicting-declarations");
        assertUnverifiable(model, "getNested", "conflicting-declarations");

        // CA-18 (fourth shape): a @RequestMapping with no method element is not GET evidence, so the route
        // is not a handler at all; its @PreAuthorize is ignored and the read stays ABSENT.
        assertAbsent(model, "getNoMethod");

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    // ---------- helpers ----------

    private static void assertUnverifiable(CapabilityModel model, String capability, String reason) {
        Capability target = named(model, capability);
        assertThat(securityUnverifiable(target)).as(capability + " unverifiable reason").contains(reason);
        assertThat(requiredAuthoritiesRaw(target)).as(capability + " must carry no authorities").isEmpty();
    }

    private static void assertAbsent(CapabilityModel model, String capability) {
        Capability target = named(model, capability);
        assertThat(requiredAuthoritiesRaw(target)).as(capability + " requiredAuthorities").isEmpty();
        assertThat(securityUnverifiable(target)).as(capability + " securityUnverifiable").isEmpty();
    }

    private static void assertNoCapabilityCarries(CapabilityModel model, String authority) {
        for (Capability capability : model.capabilities()) {
            assertThat(requiredAuthoritiesRaw(capability))
                    .as("no capability may carry the orphaned authority " + authority
                            + " (found on " + capability.name() + ")")
                    .doesNotContain(authority);
        }
    }

    // ---------- helpers ----------

    private ProviderInput input(String fixture) {
        Path source = FIXTURES.resolve(fixture);
        assertThat(Files.isDirectory(source)).as("fixture at " + source.toAbsolutePath()).isTrue();
        return new ProviderInput(source);
    }

    private static Capability named(CapabilityModel model, String name) {
        return model.capabilities().stream()
                .filter(capability -> capability.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no capability named " + name + "; capabilities were "
                        + model.capabilities().stream().map(Capability::name).toList()));
    }

    private static List<String> requiredAuthorities(CapabilityModel model, String capability) {
        return requiredAuthoritiesRaw(named(model, capability));
    }

    private static List<String> requiredAuthoritiesRaw(Capability capability) {
        return valuesOf(capability, "requiredAuthorities");
    }

    private static Optional<String> securityUnverifiable(Capability capability) {
        List<String> values = valuesOf(capability, "securityUnverifiable");
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static List<String> valuesOf(Capability capability, String key) {
        for (Annotation annotation : capability.annotations()) {
            if (annotation.key().equals(key)) {
                return annotation.values();
            }
        }
        return List.of();
    }
}
