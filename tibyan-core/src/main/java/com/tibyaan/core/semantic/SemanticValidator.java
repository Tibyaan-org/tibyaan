package com.tibyaan.core.semantic;

import com.tibyaan.core.ir.Attestation;
import com.tibyaan.core.ir.AttestationCategory;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Primitive;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The semantic validator for a {@link CapabilityModel}. It checks the rules in Volume 4
 * section 5 over the IR alone, with no knowledge of any source, framework, or backend.
 *
 * <p>Structural rules (a field always has a type, a capability always has a binding, and so
 * on) are enforced by the IR records' constructors. This validator covers the rules that span
 * the whole model: uniqueness of names, resolution of references, and the consistency of a
 * capability's read only flag with its kind. It collects every violation in one pass.
 */
public final class SemanticValidator {

    /**
     * Validates the model and returns all errors found. The result is empty when the model is
     * valid.
     */
    public ValidationResult validate(CapabilityModel model) {
        if (model == null) {
            return new ValidationResult(List.of("model must not be null"));
        }
        List<String> errors = new ArrayList<>();
        Set<String> entityNames = collectEntityNames(model);

        checkEntityIdentity(model, errors);
        checkCapabilityNames(model, errors);
        checkBindingUniqueness(model, errors);
        checkTypeReferences(model, entityNames, errors);
        checkRelations(model, entityNames, errors);
        checkReadOnlyKind(model, errors);
        checkOutputMultiple(model, errors);
        checkAttestationCompleteness(model, errors);
        checkAttestedPrefix(model, errors);
        checkInputValueConstraints(model, errors);

        return new ValidationResult(errors);
    }

    /** Validates the model and throws {@link ValidationException} if it is invalid. */
    public void validateOrThrow(CapabilityModel model) {
        validate(model).throwIfInvalid();
    }

    // Rule 3 needs the set of declared entity names even if some are duplicated, so a
    // reference to a duplicated name still resolves and is not reported twice.
    private static Set<String> collectEntityNames(CapabilityModel model) {
        Set<String> names = new HashSet<>();
        for (Entity entity : model.entities()) {
            names.add(entity.name());
        }
        return names;
    }

    // Rule 1 (Volume 4 section 5, phase Entity Identity): entity KEYS unique within the model (the
    // identity check: two entities with the same qualified key are a genuine duplicate and fail loud),
    // entity DISPLAY NAMES unique within the model (the surface-safety check, enforced independently of
    // any provider so a residual display collision fails loud here rather than shipping a duplicate tool
    // or schema), and field names unique within an entity.
    private static void checkEntityIdentity(CapabilityModel model, List<String> errors) {
        Set<String> seenKeys = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (Entity entity : model.entities()) {
            if (!seenKeys.add(entity.key())) {
                errors.add("duplicate entity key: '" + entity.key()
                        + "' (two entities share this qualified identity, a genuine duplicate)");
            }
            if (!seenNames.add(entity.name())) {
                errors.add("duplicate entity display name: '" + entity.name()
                        + "' (two distinct entities resolved to the same display name; the display-naming"
                        + " rule must disambiguate them, Volume 5 section 4.12.3)");
            }
            Set<String> seenFields = new HashSet<>();
            for (Field field : entity.fields()) {
                if (!seenFields.add(field.name())) {
                    errors.add("entity '" + entity.name() + "': duplicate field name '"
                            + field.name() + "'");
                }
            }
        }
    }

    // Rule 7 (Volume 4 section 5, phase Entity Identity; Volume 5 section 4.12.4): binding strings are
    // unique across emitted capabilities, so an unprovable binding (for example two same-simple-name
    // @Service classes both yielding OfferService#findById) is refused rather than shipped as a possible
    // runtime mis-bind. Enforced in core, provable-or-refuse, regardless of provider.
    private static void checkBindingUniqueness(CapabilityModel model, List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (Capability capability : model.capabilities()) {
            String binding = capability.binding().value();
            String prior = seen.putIfAbsent(binding, capability.name());
            if (prior != null) {
                errors.add("duplicate binding '" + binding + "' on capabilities '" + prior + "' and '"
                        + capability.name() + "' (a binding must be unique across emitted capabilities,"
                        + " Volume 4 section 5 rule 7)");
            }
        }
    }

    // Rule 2: capability names unique within the module.
    private static void checkCapabilityNames(CapabilityModel model, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (Capability capability : model.capabilities()) {
            if (!seen.add(capability.name())) {
                errors.add("duplicate capability name: '" + capability.name() + "'");
            }
        }
    }

    // Rule 3: every entity reference resolves to an entity that exists. Unknown primitives
    // cannot occur, because the closed Type system makes them unrepresentable.
    private static void checkTypeReferences(CapabilityModel model, Set<String> entityNames,
                                            List<String> errors) {
        for (Entity entity : model.entities()) {
            for (Field field : entity.fields()) {
                checkType(field.type(), entityNames, errors,
                        "field '" + entity.name() + "." + field.name() + "'");
            }
        }
        for (Capability capability : model.capabilities()) {
            for (Map.Entry<String, Type> input : capability.inputs().entrySet()) {
                checkType(input.getValue(), entityNames, errors,
                        "capability '" + capability.name() + "' input '" + input.getKey() + "'");
            }
            checkType(capability.output(), entityNames, errors,
                    "capability '" + capability.name() + "' output");
        }
    }

    private static void checkType(Type type, Set<String> entityNames, List<String> errors,
                                  String location) {
        if (type instanceof EntityRef ref && !entityNames.contains(ref.entityName())) {
            errors.add(location + " references unknown entity '" + ref.entityName() + "'");
        }
    }

    // Rule 4: every relation's from and to refer to entities that exist.
    private static void checkRelations(CapabilityModel model, Set<String> entityNames,
                                       List<String> errors) {
        for (Relation relation : model.relations()) {
            if (!entityNames.contains(relation.from())) {
                errors.add("relation references unknown entity '" + relation.from() + "' (from)");
            }
            if (!entityNames.contains(relation.to())) {
                errors.add("relation references unknown entity '" + relation.to() + "' (to)");
            }
        }
    }

    // Rule 5: a capability marked readOnly must not be of kind WRITE or ACTION.
    private static void checkReadOnlyKind(CapabilityModel model, List<String> errors) {
        for (Capability capability : model.capabilities()) {
            if (capability.readOnly() && capability.kind() != CapabilityKind.READ) {
                errors.add("capability '" + capability.name() + "' is marked readOnly but has kind "
                        + capability.kind());
            }
        }
    }

    // Rule 8 (Volume 4 section 5, rule 8; phase Attestation): an Attestation, when present, is complete,
    // and completeness is decidable from the component's own fields plus the capability's inputs (which is
    // why the component is first-class, not the Annotation hook). Every required field is present, the
    // subject entity is present-and-in-the-model for a shape claim, and the parameterMeanings keyset EQUALS
    // the capability's input set (per-input coverage, no uncovered input, no key naming a non-input). An
    // attestation on a non-READ capability fails loud: the policy gate stands, structure is never asserted.
    private void checkAttestationCompleteness(CapabilityModel model, List<String> errors) {
        Set<String> entityNames = collectEntityNames(model);
        for (Capability capability : model.capabilities()) {
            Attestation attestation = capability.attestation();
            if (attestation == null) {
                continue;
            }
            String where = "attested capability '" + capability.name() + "'";
            if (capability.kind() != CapabilityKind.READ) {
                errors.add(where + " carries an attestation but has kind " + capability.kind()
                        + "; an attestation on a write or action capability is refused (rule 8, the policy"
                        + " gate stands)");
            }
            if (isBlank(attestation.resultSemantic())) {
                errors.add(where + " has an empty resultSemantic (rule 8)");
            }
            if (isBlank(attestation.assertedBy())) {
                errors.add(where + " has an empty assertedBy (rule 8)");
            }
            if (isBlank(attestation.date())) {
                errors.add(where + " has an empty date (rule 8)");
            }
            if (isBlank(attestation.fingerprint())) {
                errors.add(where + " has an empty fingerprint (rule 8)");
            }
            if (attestation.category().requiresSubjectEntity()) {
                if (isBlank(attestation.subjectEntity())) {
                    errors.add(where + " has category '" + attestation.category().token()
                            + "' but no subjectEntity (rule 8)");
                } else if (!entityNames.contains(attestation.subjectEntity())) {
                    errors.add(where + " asserts subjectEntity '" + attestation.subjectEntity()
                            + "', which is not an entity in the model (rule 8)");
                }
            }
            checkParameterCoverage(capability, attestation, where, errors);
        }
    }

    // Rule 8, the per-input completeness the component growth was purchased for: the keyset of
    // parameterMeanings equals the capability's input set exactly. Every input is covered by a human
    // sentence, the paging mark, or the id mark (any non-empty value counts as coverage here; that the
    // mark is used only where structurally permitted is an intake-time check, Volume 5 section 4.15.2),
    // and no key names an input the capability does not have.
    private static void checkParameterCoverage(Capability capability, Attestation attestation, String where,
                                               List<String> errors) {
        Set<String> inputs = capability.inputs().keySet();
        Set<String> covered = attestation.parameterMeanings().keySet();
        for (String input : inputs) {
            String coverage = attestation.parameterMeanings().get(input);
            if (coverage == null || coverage.isBlank()) {
                errors.add(where + " leaves input '" + input + "' uncovered in parameterMeanings (rule 8,"
                        + " an uncovered name in a schema is the modeled-opaque claim the design rejects)");
            }
        }
        for (String key : covered) {
            if (!inputs.contains(key)) {
                errors.add(where + " covers '" + key + "' in parameterMeanings, which is not one of its"
                        + " inputs (rule 8, the keyset must equal the input set)");
            }
        }
    }

    // Rule 9 (Volume 4 section 5, rule 9; phase Attestation): the attested_ name prefix is reserved in
    // both directions. An attested capability must carry it (the epistemic class is readable from the name
    // alone, on every surface); a verified capability must not (the tool's prefixing never yields a false
    // attested name). Either violation fails loud.
    private static void checkAttestedPrefix(CapabilityModel model, List<String> errors) {
        for (Capability capability : model.capabilities()) {
            boolean prefixed = capability.name().startsWith(ATTESTED_PREFIX);
            if (capability.hasAttestation() && !prefixed) {
                errors.add("attested capability '" + capability.name() + "' must carry the reserved '"
                        + ATTESTED_PREFIX + "' name prefix (rule 9)");
            }
            if (!capability.hasAttestation() && prefixed) {
                errors.add("verified capability '" + capability.name() + "' carries the reserved '"
                        + ATTESTED_PREFIX + "' name prefix but has no attestation (rule 9, the prefix is"
                        + " reserved to attested capabilities in both directions)");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** The reserved attested name prefix (Volume 5 section 4.15.6), enforced in both directions by rule 9. */
    public static final String ATTESTED_PREFIX = "attested_";

    // Rule 6 (Volume 4 section 5): a capability with outputMultiple true must be of kind READ (MVP).
    // The element type is validated by rule 3 (checkTypeReferences already inspects every output),
    // and the collection-ness is a marker, not a type, so there is nothing further to check here.
    private static void checkOutputMultiple(CapabilityModel model, List<String> errors) {
        for (Capability capability : model.capabilities()) {
            if (capability.outputMultiple() && capability.kind() != CapabilityKind.READ) {
                errors.add("capability '" + capability.name() + "' has outputMultiple true but kind "
                        + capability.kind() + "; a list output is a READ in the MVP");
            }
        }
    }

    // Rule 10 (Volume 4 section 5, rule 10; phase Enum Inputs, section 3.9): input value constraints are
    // complete and well formed. Every key names an existing input; every keyed input is STRING-typed;
    // every set is non-empty with distinct values. This is what makes the phase's invariant, that a
    // rendered closed set is an enforced closed set, core-enforced rather than conventional: a constraint
    // the core cannot check is a constraint a backend could render over nothing.
    private static void checkInputValueConstraints(CapabilityModel model, List<String> errors) {
        for (Capability capability : model.capabilities()) {
            for (Map.Entry<String, List<String>> constraint : capability.inputValues().entrySet()) {
                String input = constraint.getKey();
                String where = "capability '" + capability.name() + "' constrains input '" + input + "'";

                Type type = capability.inputs().get(input);
                if (type == null) {
                    errors.add(where + ", which is not an input of that capability (rule 10)");
                    continue;
                }
                if (type != Primitive.STRING) {
                    errors.add(where + ", whose type is " + type.canonical() + ", not STRING; a closed value"
                            + " set is a set of wire strings (rule 10)");
                }
                List<String> values = constraint.getValue();
                if (values.isEmpty()) {
                    errors.add(where + " with an empty value set; a constraint that admits nothing is not"
                            + " a constraint (rule 10)");
                    continue;
                }
                Set<String> distinct = new LinkedHashSet<>(values);
                if (distinct.size() != values.size()) {
                    errors.add(where + " with duplicated values " + values + "; a closed set's values are"
                            + " distinct (rule 10)");
                }
            }
        }
    }
}
