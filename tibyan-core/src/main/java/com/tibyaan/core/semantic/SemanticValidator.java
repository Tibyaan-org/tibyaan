package com.tibyaan.core.semantic;

import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.CapabilityKind;
import com.tibyaan.core.ir.CapabilityModel;
import com.tibyaan.core.ir.Entity;
import com.tibyaan.core.ir.EntityRef;
import com.tibyaan.core.ir.Field;
import com.tibyaan.core.ir.Relation;
import com.tibyaan.core.ir.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
}
