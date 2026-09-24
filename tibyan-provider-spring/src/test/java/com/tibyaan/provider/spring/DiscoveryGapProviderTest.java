package com.tibyaan.provider.spring;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Type;
import com.tibyaan.core.semantic.SemanticValidator;
import com.tibyaan.plugin.ProviderInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Phase Discovery-Gap, the pre-registered fixtures (Volume 5, section 4.13.6). These fixtures live in
 * test resources only and are never compiled; the provider reads them as source, exactly as it reads a
 * real app. All names are synthetic (private-benchmark redaction).
 *
 * <p>The phase implements ONLY the general shapes: the inherited-{@code @Id} eligibility rule (section
 * 4.13.2) and the general core of the private-helper hop (section 4.13.3). The foundation-specific
 * shapes stay refused, and the refusal fixtures below are the proof the rules did not overreach: a
 * general rule that fired on a foundation-specific instance would be a design violation, not a bonus
 * discovery. Section 4.13.4 designs NO port-to-implementation rule, so a port the tool cannot resolve
 * to exactly one in-tree implementation stays refused (DG-5).
 */
class DiscoveryGapProviderTest {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private final SpringCapabilityProvider provider = new SpringCapabilityProvider();
    private final SemanticValidator validator = new SemanticValidator();

    // ---------- Positives, one per general rule (section 4.13.6) ----------

    @Test
    void positives_theTwoGeneralRulesDiscoverOnStandardSpringShapes() {
        ScanReport report = provider.scan(input("dg-positive"));
        CapabilityModel model = report.model();

        assertThat(model.capabilities()).extracting(Capability::name)
                .containsExactlyInAnyOrder("getNote", "getItem", "getBundleItem", "getFoo", "getBar");

        // DG-1: the child inherits its single @Id from an in-tree @MappedSuperclass. Before this phase
        // it was NO_IDENTIFIER and formed no read intent at all.
        Capability note = named(model, "getNote");
        assertThat(note.inputs()).containsExactly(entry("id", Primitive.LONG));
        assertThat(note.output()).isEqualTo(Type.entityRef("Note"));
        assertThat(note.binding().value()).isEqualTo("example.audit.NoteService#getById");

        // DG-2: the child inherits its @Id from an @Inheritance(JOINED) @Entity parent.
        Capability bundle = named(model, "getBundleItem");
        assertThat(bundle.inputs()).containsExactly(entry("id", Primitive.LONG));
        assertThat(bundle.binding().value()).isEqualTo("example.catalog.BundleItemService#getById");

        // DG-3: the clean, unguarded private-helper hop, view-returning.
        Capability foo = named(model, "getFoo");
        assertThat(foo.output()).isEqualTo(Type.entityRef("Foo"));
        assertThat(foo.binding().value()).isEqualTo("example.helper.FooService#getById");
        assertThat(foo.annotations()).anySatisfy(annotation -> {
            assertThat(annotation.key()).isEqualTo("view");
            assertThat(annotation.values()).containsExactly("FooVTO");
        });

        // DG-3b: an entity-returning read reached through a private helper stays a section 4.1 match.
        assertThat(named(model, "getBar").binding().value()).isEqualTo("example.helper.BarService#getById");

        assertThat(validator.validate(model).isValid()).isTrue();
    }

    // ---------- Refusals: the general rules must NOT overreach ----------

    @Test
    void refusals_noCapabilityIsEmittedForAnyRefusedShape() {
        // The headline guard of this phase: every shape in this fixture must stay refused. If any
        // capability appears here, a general rule fired on a shape the design refused.
        ScanReport report = provider.scan(input("dg-refusals"));

        assertThat(report.model().capabilities())
                .as("no refused shape may emit a capability; a rule that fires here overreached")
                .isEmpty();
    }

    @Test
    void dg4_aGuardedPrivateHelperIsRefusedInEitherPosition() {
        ScanReport report = provider.scan(input("dg-refusals"));

        // Post-load soft-delete guard: the pervasive benchmark case.
        assertThat(detail(report, "Gadget"))
                .contains("statement beyond the by-primary-key load")
                .contains("cannot tell a soft-delete guard from an authorization or business filter");
        // Pre-load guard: the same defect, named the same way (position-independent wording).
        assertThat(detail(report, "Widget")).contains("statement beyond the by-primary-key load");

        assertThat(cause(report, "Gadget")).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
        assertThat(cause(report, "Widget")).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
    }

    @Test
    void dg8_and_dg8b_theHelperTraceRefusesASecondHopAndAnUnresolvableHelper() {
        ScanReport report = provider.scan(input("dg-refusals"));

        // DG-8: the helper delegates further; the trace is one private hop and reads no deeper.
        assertThat(detail(report, "Chain")).contains("contains no persistence access");
        // DG-8b, the helper-resolution mis-bound guard: an overloaded helper set is never adjudicated.
        assertThat(detail(report, "Over"))
                .contains("does not resolve to a single private declaration")
                .contains("will not adjudicate");
    }

    @Test
    void theMutationAndSecondAccessDisqualifiersCarryThroughThePrivateHop() {
        ScanReport report = provider.scan(input("dg-refusals"));

        assertThat(detail(report, "Mut")).contains("performs a mutation");
        assertThat(detail(report, "Second")).contains("more than one repository access");
    }

    @Test
    void dg9_aCrossClassDelegationIsNeverSteppedInto() {
        ScanReport report = provider.scan(input("dg-refusals"));

        assertThat(detail(report, "Remote")).contains("delegation-only facade is out of scope");
    }

    @Test
    void dg5_aPortWithMoreThanOneInTreeImplementationStaysRefused() {
        // The FOUNDATION-SPECIFIC NEGATIVE. Section 4.13.4 designs no port-to-implementation rule: the
        // tool never picks an implementation by convention. More than one in-tree implementation is a
        // refusal, and it must stay one.
        ScanReport report = provider.scan(input("dg-refusals"));

        assertThat(detail(report, "Port")).contains("exactly one @Repository implementation");
        assertThat(cause(report, "Port")).isEqualTo(EntityOutcome.Cause.NO_BACKING_METHOD);
    }

    @Test
    void dg6_dg7_dg7b_dg7c_theInheritedIdentifierRefusalsAreLocatedAndDistinct() {
        ScanReport report = provider.scan(input("dg-refusals"));

        // DG-6: an unseen superclass is never assumed to carry an @Id.
        assertThat(cause(report, "Orphan")).isEqualTo(EntityOutcome.Cause.NO_IDENTIFIER);
        assertThat(detail(report, "Orphan"))
                .contains("not in the scanned source")
                .contains("never assumes an unseen superclass carries an @Id");

        // DG-7: an inherited composite key is never read by part of its key.
        assertThat(cause(report, "CompositeChild")).isEqualTo(EntityOutcome.Cause.COMPOSITE_KEY);
        assertThat(detail(report, "CompositeChild")).contains("inherits a composite key");

        // DG-7b: two ancestors each declaring an @Id is refused, distinct from a composite key, and is
        // never resolved to the nearer one.
        assertThat(cause(report, "AmbiguousChild")).isEqualTo(EntityOutcome.Cause.NO_IDENTIFIER);
        assertThat(detail(report, "AmbiguousChild"))
                .contains("ambiguous inherited identifier")
                .contains("will not pick one of two competing identifiers")
                .contains("distinct from a composite key");

        // DG-7c: an unmappable inherited id is refused exactly as a direct one is, located at the
        // ancestor that declares it.
        assertThat(cause(report, "UnmappedChild")).isEqualTo(EntityOutcome.Cause.NO_IDENTIFIER);
        assertThat(detail(report, "UnmappedChild"))
                .contains("inherited from 'UnmappedBase'")
                .contains("does not map to a bindable primitive");
    }

    // ---------- Mis-bound guards, one per new trace ----------

    @Test
    void dg10_followingAnInheritanceHierarchyNeverBindsTheWrongEntitysRead() {
        // The riskiest hop for the inherited-@Id rule: a parent and two children all take the same
        // inherited id type, so a simple-name or shape-only match could cross-bind. Each must bind its
        // own method, resolved by key.
        ScanReport report = provider.scan(input("dg-misbound"));
        CapabilityModel model = report.model();

        Capability car = named(model, "getCar");
        assertThat(car.binding().value()).isEqualTo("example.fleet.CarService#getById");
        assertThat(car.output()).isEqualTo(Type.entityRef("Car"));

        Capability truck = named(model, "getTruck");
        assertThat(truck.binding().value()).isEqualTo("example.fleet.TruckService#getById");
        assertThat(truck.output()).isEqualTo(Type.entityRef("Truck"));

        Capability vehicle = named(model, "getVehicle");
        assertThat(vehicle.binding().value()).isEqualTo("example.fleet.VehicleService#getById");
        assertThat(vehicle.output()).isEqualTo(Type.entityRef("Vehicle"));

        // No capability binds another member of the hierarchy's method.
        assertThat(car.binding().value()).isNotEqualTo(vehicle.binding().value());
        assertThat(car.binding().value()).isNotEqualTo(truck.binding().value());
    }

    @Test
    void dg11_aHelperHopNeverBindsASameNamedEntityFromAnotherContext() {
        // The riskiest hop for the helper-hop rule: two distinct entities share a simple name across
        // bounded contexts. The load repository AND the helper's return type both resolve by key, so
        // each read binds its own context's entity.
        ScanReport report = provider.scan(input("dg-misbound"));
        CapabilityModel model = report.model();

        Capability crm = named(model, "getCrmNote");
        assertThat(crm.binding().value()).isEqualTo("example.crm.CrmNoteService#getById");
        assertThat(crm.output()).isEqualTo(Type.entityRef("CrmNote"));

        Capability support = named(model, "getSupportNote");
        assertThat(support.binding().value()).isEqualTo("example.support.SupportNoteService#getById");
        assertThat(support.output()).isEqualTo(Type.entityRef("SupportNote"));

        // Neither read outputs the other context's entity.
        assertThat(crm.output()).isNotEqualTo(Type.entityRef("SupportNote"));
        assertThat(support.output()).isNotEqualTo(Type.entityRef("CrmNote"));

        assertThat(validator.validate(model).isValid()).isTrue();
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
                .orElseThrow(() -> new AssertionError("no capability named " + name
                        + "; capabilities were " + model.capabilities().stream().map(Capability::name).toList()));
    }

    private static EntityOutcome outcome(ScanReport report, String entityName) {
        return report.entityOutcomes().stream()
                .filter(o -> o.entityName().equals(entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for entity " + entityName));
    }

    private static String detail(ScanReport report, String entityName) {
        return outcome(report, entityName).detail();
    }

    private static EntityOutcome.Cause cause(ScanReport report, String entityName) {
        return outcome(report, entityName).cause();
    }
}
