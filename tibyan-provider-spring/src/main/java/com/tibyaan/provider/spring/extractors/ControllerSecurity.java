package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.tibyaan.core.ir.Annotation;
import com.tibyaan.core.ir.Capability;
import com.tibyaan.core.ir.Type;
import com.tibyaan.provider.spring.JavaTypeMapper;
import com.tibyaan.provider.spring.extractors.ControllerHandler.Call;
import com.tibyaan.provider.spring.extractors.SecurityState.Reason;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The controller-authority join (Volume 5 section 4.14): the single public entry point the provider
 * calls to decorate discovered capabilities with the declared security contract their controller
 * surface carries. It grants no discovery and mints no capability; it attaches to each existing
 * capability exactly one of the three states (Volume 5 section 4.14.5), through the Annotation hook, no
 * IR growth. A capability with no declared contract on any associated route is returned unchanged, so a
 * security-free app is byte-identical.
 *
 * <p>The controller endpoint is an ANCHOR (section 4.14.2): it confirms a read the service rules proved
 * and carries its declared authorities. Two sources never mint two capabilities for one read (the
 * pooling decision 4.14.3): a controller decorates the pooled capability, it does not add one. And the
 * mis-bound bar now covers the security contract: a contract attaches only through a strict anchor whose
 * delegation resolves to the capability's own binding, so no read ever carries another read's authorities.
 */
public final class ControllerSecurity {

    private static final String PATH_VARIABLE = "PathVariable";
    private static final String REQUEST_PARAM = "RequestParam";
    private static final String REQUEST_BODY = "RequestBody";
    private static final String REQUIRED_AUTHORITIES = "requiredAuthorities";
    private static final String SECURITY_UNVERIFIABLE = "securityUnverifiable";
    private static final Set<String> FRAMEWORK_PAGING = Set.of("Pageable", "Sort");

    private final TypeScope typeScope;
    private final AuthorityCompiler compiler;
    private final JavaTypeMapper typeMapper;
    private final List<ControllerHandler> handlers;
    private final Map<String, List<ServiceInfo>> serviceByTypeFqcn;
    private final Map<String, String> boundReturnByBinding;
    private final Map<String, List<ServiceMethodSource>> boundSourcesByBinding;

    private ControllerSecurity(TypeScope typeScope, AuthorityCompiler compiler, JavaTypeMapper typeMapper,
                               List<ControllerHandler> handlers,
                               Map<String, List<ServiceInfo>> serviceByTypeFqcn,
                               Map<String, String> boundReturnByBinding,
                               Map<String, List<ServiceMethodSource>> boundSourcesByBinding) {
        this.typeScope = typeScope;
        this.compiler = compiler;
        this.typeMapper = typeMapper;
        this.handlers = handlers;
        this.serviceByTypeFqcn = serviceByTypeFqcn;
        this.boundReturnByBinding = boundReturnByBinding;
        this.boundSourcesByBinding = boundSourcesByBinding;
    }

    /**
     * Decorate each capability with its declared-security state. Returns a new list in the same order;
     * a capability whose state is ABSENT is returned unchanged (byte-identical output for a security-free
     * app). This is the provider's join step, run after by-id and list discovery have produced the pool.
     *
     * @param units          the parsed source
     * @param serviceSources the @Service method catalog (the binding targets to resolve against)
     * @param capabilities   the discovered capabilities to decorate
     * @param typeMapper      the Java-to-primitive mapper, for the by-id path-variable id check
     */
    public static List<Capability> decorate(List<CompilationUnit> units,
                                            List<ServiceMethodSource> serviceSources,
                                            List<Capability> capabilities, JavaTypeMapper typeMapper) {
        TypeScope typeScope = TypeScope.from(units);
        ConstantIndex constants = ConstantIndex.from(units, typeScope);
        SecurityGate gate = SecurityGate.from(units);
        AuthorityCompiler compiler = new AuthorityCompiler(gate, constants);
        List<ControllerHandler> handlers = new ControllerExtractor().handlers(units, typeScope);

        Map<String, List<ServiceInfo>> serviceByTypeFqcn = new HashMap<>();
        Map<String, String> boundReturnByBinding = new HashMap<>();
        Map<String, List<ServiceMethodSource>> boundSourcesByBinding = new HashMap<>();
        Set<String> seenService = new HashSet<>();
        for (ServiceMethodSource source : serviceSources) {
            String binding = source.method().toBinding();
            boundReturnByBinding.putIfAbsent(binding, source.method().returnTypeSimpleName());
            boundSourcesByBinding.computeIfAbsent(binding, k -> new ArrayList<>()).add(source);
            ClassOrInterfaceDeclaration service = source.service();
            String fqcn = service.getFullyQualifiedName().orElse(null);
            if (fqcn == null || !seenService.add(fqcn)) {
                continue;
            }
            ServiceInfo info = new ServiceInfo(service.getNameAsString());
            serviceByTypeFqcn.computeIfAbsent(fqcn, k -> new ArrayList<>()).add(info);
            CompilationUnit serviceCu = service.findCompilationUnit().orElse(null);
            for (ClassOrInterfaceType implemented : service.getImplementedTypes()) {
                typeScope.resolveFqcn(implemented.getNameWithScope(), serviceCu).ifPresent(ifaceFqcn ->
                        serviceByTypeFqcn.computeIfAbsent(ifaceFqcn, k -> new ArrayList<>()).add(info));
            }
        }

        ControllerSecurity join = new ControllerSecurity(typeScope, compiler, typeMapper, handlers,
                serviceByTypeFqcn, boundReturnByBinding, boundSourcesByBinding);
        List<Capability> decorated = new ArrayList<>();
        for (Capability capability : capabilities) {
            decorated.add(join.decorateOne(capability));
        }
        return decorated;
    }

    private Capability decorateOne(Capability capability) {
        SecurityState state = stateOf(capability);
        return switch (state.state()) {
            case ABSENT -> capability;
            case COMPILED -> withAnnotation(capability, new Annotation(REQUIRED_AUTHORITIES, state.authorities()));
            case UNVERIFIABLE -> withAnnotation(capability,
                    new Annotation(SECURITY_UNVERIFIABLE, List.of(state.reason().token())));
        };
    }

    // The composition rule (section 4.14.4). Gather the capability's associated GET handlers and its
    // bound method's own declarations. No declaration anywhere is ABSENT. Otherwise a contract is
    // declared, and it compiles only when every associated route is a strict anchor, every declaration is
    // in the compilation set, and all agree; every divergence is a named-reason refusal, never partial.
    private SecurityState stateOf(Capability capability) {
        String binding = capability.binding().value();
        boolean list = capability.outputMultiple();
        Type idType = list ? null : capability.inputs().values().stream().findFirst().orElse(null);
        String boundReturn = boundReturnByBinding.get(binding);

        List<ControllerHandler> associated = new ArrayList<>();
        for (ControllerHandler handler : handlers) {
            if (isAssociated(handler, binding)) {
                associated.add(handler);
            }
        }
        List<SecurityDeclaration> boundDecls = boundMethodDeclarations(binding);

        boolean detected = !boundDecls.isEmpty()
                || associated.stream().anyMatch(h -> !h.effectiveDeclarations().isEmpty());
        if (!detected) {
            return SecurityState.absent();
        }
        // Condition 4: a read reachable through an associated route the tool cannot prove clean (a
        // non-strict anchor) means the declared contract is not uniformly enforceable; refuse.
        for (ControllerHandler handler : associated) {
            if (!isStrictAnchor(handler, binding, idType, list, boundReturn)) {
                return SecurityState.unverifiable(Reason.CONFLICTING_DECLARATIONS);
            }
        }

        List<SecurityState> contributions = new ArrayList<>();
        for (ControllerHandler handler : associated) {
            contributions.add(handler.effectiveDeclarations().isEmpty()
                    // An undeclared strict route alongside a declared one: the read is reachable without
                    // the declared contract, so the contract cannot be presented as complete.
                    ? SecurityState.unverifiable(Reason.CONFLICTING_DECLARATIONS)
                    : compiler.compile(handler.effectiveDeclarations()));
        }
        if (!boundDecls.isEmpty()) {
            contributions.add(compiler.compile(boundDecls));
        }
        return reduce(contributions);
    }

    // The most severe reason wins deterministically (the Reason enum's declaration order is its
    // precedence); if every contribution compiled, they must all be the identical any-of sequence, else
    // it is a conflict. Sequence equality (values and order) is the ratified agreement test.
    private static SecurityState reduce(List<SecurityState> contributions) {
        Optional<Reason> worst = contributions.stream()
                .filter(SecurityState::isUnverifiable)
                .map(SecurityState::reason)
                .min(Comparator.comparingInt(Enum::ordinal));
        if (worst.isPresent()) {
            return SecurityState.unverifiable(worst.get());
        }
        List<List<String>> sequences = contributions.stream()
                .map(SecurityState::authorities)
                .distinct()
                .toList();
        return sequences.size() == 1
                ? SecurityState.compiled(sequences.get(0))
                : SecurityState.unverifiable(Reason.CONFLICTING_DECLARATIONS);
    }

    // Associated: the handler's body makes, anywhere, a field delegation that resolves to this binding
    // (section 4.14.2). Strictness is decided separately; association is by the delegation alone.
    private boolean isAssociated(ControllerHandler handler, String binding) {
        return handler.bodyDelegations().stream()
                .anyMatch(call -> resolveBinding(handler, call).map(binding::equals).orElse(false));
    }

    // A strict anchor (conditions 3 to 6). The single-statement body (condition 4) is already decided by
    // the extractor; here the delegation must target this binding, the parameter shape must fit the
    // capability kind, the argument must be the mapped parameter verbatim, and the response body type
    // must match the bound method's return (the override-variance guard).
    private boolean isStrictAnchor(ControllerHandler handler, String binding, Type idType, boolean list,
                                   String boundReturn) {
        if (!handler.strictSingleStatement()) {
            return false;
        }
        if (!resolveBinding(handler, handler.strictDelegation()).map(binding::equals).orElse(false)) {
            return false;
        }
        MethodDeclaration mapping = handler.mappingDecl();
        if (list ? !listParametersOk(mapping) : !byIdParametersOk(mapping, idType)) {
            return false;
        }
        if (!argumentsVerbatim(handler, list)) {
            return false;
        }
        return boundReturn == null || returnConsistent(mapping, boundReturn);
    }

    // Condition 3, by-id: exactly one parameter, annotated @PathVariable (and not @RequestParam or
    // @RequestBody), mapping to the entity's id type. A query parameter is a filtered read, refused per
    // the E6b verdict; it anchors nothing.
    private boolean byIdParametersOk(MethodDeclaration mapping, Type idType) {
        if (mapping.getParameters().size() != 1 || idType == null) {
            return false;
        }
        Parameter parameter = mapping.getParameter(0);
        boolean pathVariable = hasParamAnnotation(parameter, PATH_VARIABLE)
                && !hasParamAnnotation(parameter, REQUEST_PARAM)
                && !hasParamAnnotation(parameter, REQUEST_BODY);
        return pathVariable && typeMapper.map(parameter.getType().asString())
                .map(idType::equals).orElse(false);
    }

    // Condition 3, list: no parameters, or exactly one framework paging parameter (Pageable or Sort),
    // recognized structurally by type, forwarded verbatim (the mirror of section 4.10 condition 3).
    private static boolean listParametersOk(MethodDeclaration mapping) {
        if (mapping.getParameters().isEmpty()) {
            return true;
        }
        if (mapping.getParameters().size() != 1) {
            return false;
        }
        return FRAMEWORK_PAGING.contains(simpleName(mapping.getParameter(0).getType().asString()));
    }

    // Condition 5: the delegation argument is the handler's own mapped parameter, verbatim. For by-id the
    // single id parameter; for a paged list the single Pageable/Sort parameter; for a bare list no
    // argument. The extractor already ensured every strict-delegation argument is a bare name.
    private static boolean argumentsVerbatim(ControllerHandler handler, boolean list) {
        List<Parameter> implParams = handler.implMethod().getParameters();
        List<Expression> args = handler.strictDelegation().arguments();
        if (!list) {
            return implParams.size() == 1 && args.size() == 1 && nameMatches(args.get(0), implParams.get(0));
        }
        if (implParams.isEmpty()) {
            return args.isEmpty();
        }
        return implParams.size() == 1 && args.size() == 1 && nameMatches(args.get(0), implParams.get(0));
    }

    private static boolean nameMatches(Expression argument, Parameter parameter) {
        return argument instanceof NameExpr name
                && name.getNameAsString().equals(parameter.getNameAsString());
    }

    // Condition 6: the response body type (one ResponseEntity layer unwrapped) matches the bound
    // method's declared return type. In a one-statement delegation this holds by construction; it is
    // checked so an override-variance trick cannot slip a differently-typed surface past the anchor.
    private static boolean returnConsistent(MethodDeclaration mapping, String boundReturn) {
        return simpleName(unwrapResponseEntity(mapping.getType().asString())).equals(boundReturn);
    }

    private static String unwrapResponseEntity(String type) {
        String trimmed = type.trim();
        int open = trimmed.indexOf('<');
        if (open < 0 || !simpleName(trimmed.substring(0, open)).equals("ResponseEntity")) {
            return trimmed;
        }
        int close = trimmed.lastIndexOf('>');
        return close > open ? trimmed.substring(open + 1, close).trim() : trimmed;
    }

    // Resolve a controller field delegation to a binding string: the field's declared type resolves in
    // tree (the section 4.12.4 scoping) to exactly one @Service class, directly or as the sole in-tree
    // implementation of the interface it is typed by; more than one implementation refuses (4.13.4).
    private Optional<String> resolveBinding(ControllerHandler handler, Call call) {
        return handler.controllerClass().getFieldByName(call.fieldName())
                .map(field -> field.getElementType().asString())
                .flatMap(fieldType -> handler.controllerClass().findCompilationUnit()
                        .flatMap(cu -> typeScope.resolveFqcn(fieldType, cu)))
                .map(serviceByTypeFqcn::get)
                .filter(infos -> infos != null && infos.size() == 1)
                .map(infos -> infos.get(0).simpleName() + "#" + call.methodName());
    }

    // The bound service method's own effective declarations (section 4.14.4 composition, step 1): a
    // declaration on the service method itself is a declaration on every route through it. Services carry
    // none in any current benchmark, but the rule covers it.
    private List<SecurityDeclaration> boundMethodDeclarations(String binding) {
        List<ServiceMethodSource> sources = boundSourcesByBinding.getOrDefault(binding, List.of());
        if (sources.isEmpty()) {
            return List.of();
        }
        ServiceMethodSource source = sources.get(0);
        return SecurityDeclarations.effective(List.of(
                SecurityDeclarations.AnnotatedNode.of(source.declaration()),
                SecurityDeclarations.AnnotatedNode.of(source.service())));
    }

    private static Capability withAnnotation(Capability capability, Annotation annotation) {
        List<Annotation> annotations = new ArrayList<>(capability.annotations());
        annotations.add(annotation);
        // Every component is carried through, inputValues included: adding a security annotation must
        // never drop an input value constraint, which would leave a rendered closed set unbacked.
        return new Capability(capability.name(), capability.kind(), capability.inputs(),
                capability.output(), capability.outputMultiple(), capability.readOnly(),
                capability.binding(), annotations, capability.attestation(), capability.inputValues());
    }

    private static boolean hasParamAnnotation(Parameter parameter, String simpleName) {
        return parameter.getAnnotations().stream()
                .anyMatch(annotation -> simpleName.equals(simpleName(annotation.getNameAsString())));
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    // The identity of a resolved @Service class: its simple name, which the binding string uses.
    private record ServiceInfo(String simpleName) {
    }
}
