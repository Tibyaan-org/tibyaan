package com.tibyaan.provider.spring.draft;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.tibyaan.provider.spring.attest.ReadSafety;
import com.tibyaan.provider.spring.extractors.RepositoryCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * DL-1.1, the bean-call closure of the READ label (Volume 5 section 4.17.14, ratified 2026-09-23).
 *
 * <p>Read-safety reads a method body, its one-hop private helpers and the repository declarations it calls. It does
 * not follow a call into another bean, and the reading sample of as-backend (as-backend-trial.md section 10.2) found
 * every false READ there: a JMS publisher, a counter service three hops away, and a reflective dispatcher. This
 * class classifies every call in the slice whose receiver is a FIELD of the declaring class, and the most severe
 * classification wins (WRITE over UNKNOWN over READ):
 *
 * <ul>
 *   <li>C1, a repository field: already covered by read-safety, skipped here.</li>
 *   <li>C2, an in-tree bean: the field type resolves to exactly one concrete stereotyped class, the called method is
 *       resolved by name and arity and labeled recursively, bounded at four bean hops (decision D1) and cycle-safe.
 *       Two candidate implementations give UNKNOWN (D2). A call to the same class's own non-helper method is
 *       followed within the bean without counting a hop (a build decision, section 4.17.15).</li>
 *   <li>C3, a messaging, mail or event type: WRITE, an external effect. A closed set.</li>
 *   <li>C4, a remote client: a {@code @FeignClient} GET or a {@code RestTemplate} {@code getFor*} call is READ with
 *       the remote call named (D3); any other verb is WRITE (Feign) or UNKNOWN (RestTemplate, WebClient).</li>
 *   <li>C5, reflection: {@code getBean} or {@code getDeclaredMethod} anywhere in the slice gives UNKNOWN.</li>
 *   <li>C6, anything else: a field type that does not resolve in the tree and is not in the closed PURE set gives
 *       UNKNOWN.</li>
 * </ul>
 *
 * <p>This refines what a DRAFT's READ label claims. It does not touch the attestation intake's read-safety check.
 * It keeps its own simple-name index, interfaces included, because the shared type resolver indexes classes and
 * enums only (it keys entities) and a bean field is usually typed by an interface.
 */
public final class BeanCallClosure {

    /** Decision D1: the bean-hop bound. */
    public static final int MAX_HOPS = 4;

    // C3: the closed set of messaging, mail and event types. Extending it is a design change.
    private static final Set<String> EXTERNAL_EFFECT_TYPES = Set.of(
            "JmsTemplate", "JmsMessagingTemplate", "KafkaTemplate", "RabbitTemplate", "AmqpTemplate",
            "StreamBridge", "ApplicationEventPublisher", "JavaMailSender", "SimpMessagingTemplate");

    // C6: the closed PURE set of library types a READ may call without losing the label. JDK types are pure by
    // their import package (java.*), see isPureLibraryType.
    private static final Set<String> PURE_TYPES = Set.of(
            "ObjectMapper", "Clock", "Logger", "String", "Integer", "Long", "Boolean", "Double", "BigDecimal",
            "List", "Map", "Set", "Optional", "Collection",
            // DL-1.2 A3 (section 4.17.16): computations over their arguments.
            "Gson", "PasswordEncoder");

    // DL-1.2 A1: a configuration class is a bean, so an abstract config resolves to its configured subclass.
    private static final Set<String> STEREOTYPES = Set.of("Service", "Component", "Repository",
            "Configuration", "ConfigurationProperties");

    // DL-1.2 A2: a provider field is transparent; these calls obtain the bean and mutate nothing.
    private static final Set<String> PROVIDER_TYPES = Set.of("ObjectProvider", "Provider", "Supplier");
    private static final Set<String> PROVIDER_GETTERS = Set.of("get", "getObject", "getIfAvailable", "getIfUnique");

    // DL-1.2 A4: fluent HTTP clients carry their verb in the call.
    private static final Set<String> FLUENT_CLIENTS = Set.of("RestClient", "WebClient");
    private static final Set<String> FLUENT_MUTATING_VERBS = Set.of("post", "put", "patch", "delete");

    private static final Set<String> REFLECTIVE_CALLS = Set.of("getBean", "getDeclaredMethod");

    /** The severity-ordered outcome of one classification. */
    public enum Severity { READ, UNKNOWN, WRITE }

    /**
     * The closure's finding for one method.
     *
     * @param severity the most severe classification found
     * @param reason   the located reason for a WRITE or UNKNOWN, or the remote calls a READ makes; empty when a
     *                 READ makes none
     */
    public record Finding(Severity severity, String reason) {
        static Finding read() {
            return new Finding(Severity.READ, "");
        }
    }

    private final RepositoryCatalog catalog;
    private final ReadSafety readSafety;
    private final Map<String, ClassOrInterfaceDeclaration> byFqcn = new HashMap<>();
    private final Map<String, List<ClassOrInterfaceDeclaration>> implementorsByFqcn = new HashMap<>();
    private final Map<String, List<String>> fqcnsBySimpleName = new HashMap<>();

    public BeanCallClosure(List<CompilationUnit> units, RepositoryCatalog catalog, ReadSafety readSafety) {
        this.catalog = catalog;
        this.readSafety = readSafety;
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                type.getFullyQualifiedName().ifPresent(fqcn -> {
                    byFqcn.put(fqcn, type);
                    fqcnsBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new ArrayList<>()).add(fqcn);
                });
            }
        }
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() || type.isAbstract()) {
                    continue;
                }
                // An abstract class resolves to its concrete subclasses exactly as an interface resolves to its
                // implementations (C2): both are "the one concrete class the field type stands for".
                List<ClassOrInterfaceType> supertypes = new ArrayList<>(type.getImplementedTypes());
                supertypes.addAll(type.getExtendedTypes());
                for (ClassOrInterfaceType supertype : supertypes) {
                    Resolved r = resolveType(supertype.getNameAsString(), unit);
                    if (r.fqcn() != null) {
                        implementorsByFqcn.computeIfAbsent(r.fqcn(), k -> new ArrayList<>()).add(type);
                    }
                }
            }
        }
    }

    /**
     * Classifies the field calls of a method whose own slice read-safety already found clean or undecided.
     *
     * @param owner   the declaring class
     * @param method  the method
     * @param helpers the one-hop private helpers read-safety also read
     */
    public Finding classify(ClassOrInterfaceDeclaration owner, MethodDeclaration method,
                            List<MethodDeclaration> helpers) {
        Set<MethodDeclaration> path = identitySet();
        path.add(method);
        return classify(owner, method, helpers, path, 0, owner.getNameAsString() + "#" + method.getNameAsString());
    }

    private Finding classify(ClassOrInterfaceDeclaration owner, MethodDeclaration method,
                             List<MethodDeclaration> helpers, Set<MethodDeclaration> path, int hops, String chain) {
        Map<String, String> fieldTypes = fieldTypes(owner);
        Optional<CompilationUnit> cu = owner.findCompilationUnit();
        Finding worst = Finding.read();
        Set<String> remoteReads = new LinkedHashSet<>();

        List<MethodDeclaration> bodies = new ArrayList<>();
        bodies.add(method);
        bodies.addAll(helpers);
        for (MethodDeclaration body : bodies) {
            Set<String> shadowing = localNames(body);
            for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
                String name = call.getNameAsString();
                if (REFLECTIVE_CALLS.contains(name)) {
                    worst = worse(worst, new Finding(Severity.UNKNOWN, "a reflective call '" + name + "' in " + chain
                            + " chooses the executed code at run time (bean-call closure, section 4.17.14, C5)"));
                    continue;
                }
                Optional<String> field = fieldReceiver(call, fieldTypes, shadowing);
                if (field.isEmpty() && cu.isPresent()) {
                    // DL-1.2 A2: a call chained directly on a provider's result, provider.getIfAvailable().m(), is
                    // classified as a call on a field of the provided type T.
                    Optional<String> provided = providedType(call, fieldTypes, shadowing);
                    if (provided.isPresent()) {
                        Finding one = classifyFieldCall(call, providerFieldName(call), provided.get(), cu.get(), path,
                                hops, chain, remoteReads);
                        worst = worse(worst, one);
                        if (worst.severity() == Severity.WRITE) {
                            return worst;
                        }
                        continue;
                    }
                }
                if (field.isEmpty() && isSelfCall(call) && !isHelper(call, helpers)) {
                    // Build decision (section 4.17.15): a call to the same class's own non-helper method (an overload,
                    // a public delegate) is followed within the bean without counting a hop. Without it the counter
                    // write of the reading sample stays hidden behind a one-argument overload.
                    worst = worse(worst, recurseWithin(call, owner, path, hops, chain));
                    if (worst.severity() == Severity.WRITE) {
                        return worst;
                    }
                    continue;
                }
                if (field.isEmpty() || cu.isEmpty()) {
                    continue;
                }
                Finding one = classifyFieldCall(call, field.get(), fieldTypes.get(field.get()), cu.get(), path, hops,
                        chain, remoteReads);
                worst = worse(worst, one);
                if (worst.severity() == Severity.WRITE) {
                    return worst;
                }
            }
        }
        if (worst.severity() == Severity.READ && !remoteReads.isEmpty()) {
            return new Finding(Severity.READ, "makes remote read calls: " + String.join(", ", remoteReads));
        }
        return worst;
    }

    private Finding classifyFieldCall(MethodCallExpr call, String field, String declaredType, CompilationUnit cu,
                                      Set<MethodDeclaration> path, int hops, String chain, Set<String> remoteReads) {
        String simple = simpleName(declaredType);
        String where = "the call '" + field + "." + call.getNameAsString() + "' in " + chain;

        if (catalog.isRepositoryType(simple)) {
            return Finding.read(); // C1: read-safety already read the repository declaration.
        }
        if (EXTERNAL_EFFECT_TYPES.contains(simple)) {
            return new Finding(Severity.WRITE, where + " goes to " + simple
                    + ", an external effect (bean-call closure, section 4.17.14, C3)");
        }
        if (PROVIDER_TYPES.contains(simple)) {
            // DL-1.2 A2: obtaining the bean is READ; what is then called on it is classified by the caller.
            return PROVIDER_GETTERS.contains(call.getNameAsString())
                    ? Finding.read()
                    : new Finding(Severity.UNKNOWN, where + " is not a provider lookup (A2)");
        }
        if (simple.equals("RestTemplate")) {
            if (call.getNameAsString().startsWith("getFor")) {
                remoteReads.add("RestTemplate." + call.getNameAsString() + " in " + chain);
                return Finding.read();
            }
            return new Finding(Severity.UNKNOWN, where + " is a RestTemplate call that is not a GET (C4)");
        }
        if (FLUENT_CLIENTS.contains(simple)) {
            // DL-1.2 A4: the verb is explicit in the call, exactly as on a Feign method.
            String verb = call.getNameAsString();
            if (verb.equals("get")) {
                remoteReads.add(simple + ".get in " + chain);
                return Finding.read();
            }
            if (FLUENT_MUTATING_VERBS.contains(verb)) {
                return new Finding(Severity.WRITE, where + " is a " + simple + " " + verb.toUpperCase()
                        + ", a remote mutation (C4, A4)");
            }
            return new Finding(Severity.UNKNOWN, where + " is a " + simple + " call whose verb is not explicit (C4)");
        }

        Resolved resolution = resolveType(declaredType, cu);
        if (resolution.ambiguous()) {
            return new Finding(Severity.UNKNOWN, where + ": the type '" + simple + "' is ambiguous in the tree (C2)");
        }
        if (resolution.fqcn() == null) {
            if (isPureLibraryType(simple, cu)) {
                return Finding.read();
            }
            return new Finding(Severity.UNKNOWN, where + ": the type '" + simple
                    + "' does not resolve in the tree and is not in the closed pure set (C6)");
        }
        ClassOrInterfaceDeclaration declared = byFqcn.get(resolution.fqcn());
        if (declared == null) {
            return new Finding(Severity.UNKNOWN, where + ": '" + simple + "' is not a class or interface (C6)");
        }
        if (declared.isInterface() && hasAnnotation(declared, "FeignClient")) {
            return feign(call, declared, where, chain, remoteReads);
        }
        if (hasAnnotation(declared, "Mapper")) {
            return Finding.read(); // PURE set: a MapStruct mapper, whose generated implementation is not in the tree.
        }

        ClassOrInterfaceDeclaration bean;
        if (!declared.isInterface() && !declared.isAbstract()) {
            bean = declared;
        } else {
            List<ClassOrInterfaceDeclaration> impls = implementorsByFqcn.getOrDefault(resolution.fqcn(), List.of())
                    .stream().filter(BeanCallClosure::hasStereotype).toList();
            if (impls.isEmpty()) {
                return new Finding(Severity.UNKNOWN, where + ": '" + simple
                        + "' has no in-tree stereotyped implementation (C6)");
            }
            if (impls.size() > 1) {
                // DL-1.2 A5 (D2 amended): the runtime picks one implementation, so the label is sound whichever it
                // picks only if every implementation is labeled and the most severe wins.
                Finding worst = Finding.read();
                List<String> parts = new ArrayList<>();
                for (ClassOrInterfaceDeclaration impl : impls) {
                    Finding one = recurse(call, impl, where, path, hops, chain);
                    parts.add(impl.getNameAsString() + " " + one.severity());
                    worst = worse(worst, one);
                }
                if (worst.severity() == Severity.READ) {
                    return Finding.read();
                }
                return new Finding(worst.severity(), where + ": '" + simple + "' has " + impls.size()
                        + " in-tree implementations (" + String.join(", ", parts) + "), the most severe wins"
                        + " (C2, decision D2 as amended by A5); " + worst.reason());
            }
            bean = impls.get(0);
        }
        if (!hasStereotype(bean)) {
            return new Finding(Severity.UNKNOWN, where + ": '" + bean.getNameAsString()
                    + "' is not a Spring stereotyped bean (C6)");
        }
        return recurse(call, bean, where, path, hops, chain);
    }

    // A same-class call: follow the target within this bean, no hop counted.
    private Finding recurseWithin(MethodCallExpr call, ClassOrInterfaceDeclaration owner, Set<MethodDeclaration> path,
                                  int hops, String chain) {
        Optional<MethodDeclaration> target = findMethod(owner, call.getNameAsString(), call.getArguments().size());
        if (target.isEmpty()) {
            long sameArity = owner.getMethodsByName(call.getNameAsString()).stream()
                    .filter(m -> m.getParameters().size() == call.getArguments().size()).count();
            return sameArity > 1
                    ? new Finding(Severity.UNKNOWN, "the self call '" + call.getNameAsString() + "' in " + chain
                            + " matches " + sameArity + " overloads of one arity (C2)")
                    : Finding.read(); // No in-tree body: inherited from a library type.
        }
        if (path.contains(target.get())) {
            return Finding.read();
        }
        return step(owner, target.get(), path, hops, chain);
    }

    private Finding recurse(MethodCallExpr call, ClassOrInterfaceDeclaration bean, String where,
                            Set<MethodDeclaration> path, int hops, String chain) {
        Optional<MethodDeclaration> target = findMethod(bean, call.getNameAsString(), call.getArguments().size());
        if (target.isEmpty()) {
            return new Finding(Severity.UNKNOWN, where + ": no single method '" + call.getNameAsString() + "' of arity "
                    + call.getArguments().size() + " on '" + bean.getNameAsString() + "' (C2)");
        }
        MethodDeclaration callee = target.get();
        if (path.contains(callee)) {
            return Finding.read(); // Cycle-safe: a method already on the path contributes READ.
        }
        if (hops + 1 > MAX_HOPS) {
            return new Finding(Severity.UNKNOWN, where + " exceeds the bound of " + MAX_HOPS
                    + " bean hops (C2, decision D1)");
        }
        return step(bean, callee, path, hops + 1, chain);
    }

    private Finding step(ClassOrInterfaceDeclaration bean, MethodDeclaration callee, Set<MethodDeclaration> path,
                         int hops, String chain) {
        String nextChain = chain + " -> " + bean.getNameAsString() + "#" + callee.getNameAsString();
        List<MethodDeclaration> helpers = DraftLabeler.directPrivateHelpers(bean, callee);
        Optional<String> refusal = readSafety.check(bean, callee, helpers);
        if (refusal.isPresent()) {
            Severity severity = DraftLabeler.isWrite(refusal.get()) ? Severity.WRITE : Severity.UNKNOWN;
            return new Finding(severity, "via " + nextChain + ": " + refusal.get() + " (bean-call closure, C2)");
        }
        Set<MethodDeclaration> nextPath = identitySet();
        nextPath.addAll(path);
        nextPath.add(callee);
        return classify(bean, callee, helpers, nextPath, hops, nextChain);
    }

    private static Finding feign(MethodCallExpr call, ClassOrInterfaceDeclaration client, String where, String chain,
                                 Set<String> remoteReads) {
        for (MethodDeclaration m : client.getMethods()) {
            if (!m.getNameAsString().equals(call.getNameAsString())
                    || m.getParameters().size() != call.getArguments().size()) {
                continue;
            }
            if (isGet(m)) {
                remoteReads.add(client.getNameAsString() + "." + m.getNameAsString() + " (Feign GET) in " + chain);
                return Finding.read();
            }
            return new Finding(Severity.WRITE, where + " is a Feign call that is not a GET (C4)");
        }
        return new Finding(Severity.UNKNOWN, where + ": no matching method on the Feign client '"
                + client.getNameAsString() + "' (C4)");
    }

    private static boolean isGet(MethodDeclaration m) {
        for (AnnotationExpr a : m.getAnnotations()) {
            String n = a.getNameAsString();
            if (n.equals("GetMapping")) {
                return true;
            }
            if (n.equals("RequestMapping")) {
                return a.toString().contains("RequestMethod.GET") || a.toString().contains("method = GET");
            }
        }
        return false;
    }

    private Optional<MethodDeclaration> findMethod(ClassOrInterfaceDeclaration type, String name, int arity) {
        ClassOrInterfaceDeclaration current = type;
        Set<ClassOrInterfaceDeclaration> seen = identityTypeSet();
        while (current != null && seen.add(current)) {
            List<MethodDeclaration> matches = current.getMethodsByName(name).stream()
                    .filter(m -> m.getParameters().size() == arity && m.getBody().isPresent())
                    .toList();
            if (matches.size() == 1) {
                return Optional.of(matches.get(0));
            }
            if (matches.size() > 1) {
                return Optional.empty();
            }
            current = superclassOf(current);
        }
        return Optional.empty();
    }

    private ClassOrInterfaceDeclaration superclassOf(ClassOrInterfaceDeclaration type) {
        if (type.getExtendedTypes().isEmpty() || type.isInterface()) {
            return null;
        }
        Optional<CompilationUnit> cu = type.findCompilationUnit();
        if (cu.isEmpty()) {
            return null;
        }
        Resolved r = resolveType(type.getExtendedTypes(0).getNameAsString(), cu.get());
        return r.fqcn() != null ? byFqcn.get(r.fqcn()) : null;
    }

    // DL-1.2 A2: for a call whose scope is provider.get*() on a provider-typed field, the provided type T.
    private static Optional<String> providedType(MethodCallExpr call, Map<String, String> fieldTypes,
                                                 Set<String> shadowing) {
        if (call.getScope().isEmpty() || !call.getScope().get().isMethodCallExpr()) {
            return Optional.empty();
        }
        MethodCallExpr inner = call.getScope().get().asMethodCallExpr();
        if (!PROVIDER_GETTERS.contains(inner.getNameAsString())) {
            return Optional.empty();
        }
        Optional<String> field = fieldReceiver(inner, fieldTypes, shadowing);
        if (field.isEmpty()) {
            return Optional.empty();
        }
        String declared = fieldTypes.get(field.get());
        if (!PROVIDER_TYPES.contains(simpleName(declared))) {
            return Optional.empty();
        }
        int lt = declared.indexOf('<');
        int gt = declared.lastIndexOf('>');
        return lt >= 0 && gt > lt ? Optional.of(declared.substring(lt + 1, gt).trim()) : Optional.empty();
    }

    private static String providerFieldName(MethodCallExpr call) {
        MethodCallExpr inner = call.getScope().get().asMethodCallExpr();
        return inner.getScope().map(s -> s.isNameExpr() ? s.asNameExpr().getNameAsString()
                : s.asFieldAccessExpr().getNameAsString()).orElse("provider") + "." + inner.getNameAsString() + "()";
    }

    private static boolean isSelfCall(MethodCallExpr call) {
        return call.getScope().isEmpty() || call.getScope().get().isThisExpr();
    }

    private static boolean isHelper(MethodCallExpr call, List<MethodDeclaration> helpers) {
        for (MethodDeclaration h : helpers) {
            if (h.getNameAsString().equals(call.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> fieldReceiver(MethodCallExpr call, Map<String, String> fieldTypes,
                                                  Set<String> shadowing) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        String name = null;
        if (scope.isNameExpr()) {
            name = scope.asNameExpr().getNameAsString();
            if (shadowing.contains(name)) {
                return Optional.empty();
            }
        } else if (scope.isFieldAccessExpr() && scope.asFieldAccessExpr().getScope().isThisExpr()) {
            name = scope.asFieldAccessExpr().getNameAsString();
        }
        return name != null && fieldTypes.containsKey(name) ? Optional.of(name) : Optional.empty();
    }

    private static Set<String> localNames(MethodDeclaration method) {
        Set<String> names = new HashSet<>();
        for (Parameter p : method.getParameters()) {
            names.add(p.getNameAsString());
        }
        method.getBody().ifPresent(b -> b.findAll(VariableDeclarator.class).forEach(v -> names.add(v.getNameAsString())));
        return names;
    }

    private static Map<String, String> fieldTypes(ClassOrInterfaceDeclaration owner) {
        Map<String, String> types = new HashMap<>();
        for (FieldDeclaration field : owner.getFields()) {
            for (VariableDeclarator v : field.getVariables()) {
                types.put(v.getNameAsString(), v.getType().asString());
            }
        }
        return types;
    }

    private static boolean isPureLibraryType(String simple, CompilationUnit cu) {
        if (PURE_TYPES.contains(simple)) {
            return true;
        }
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isAsterisk() && lastSegment(imp.getNameAsString()).equals(simple)) {
                return imp.getNameAsString().startsWith("java.");
            }
        }
        return false;
    }

    private static boolean hasStereotype(ClassOrInterfaceDeclaration type) {
        for (String s : STEREOTYPES) {
            if (hasAnnotation(type, s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream().anyMatch(a -> lastSegment(a.getNameAsString()).equals(simpleName));
    }

    // JavaParser nodes compare STRUCTURALLY, so two methods with identical bodies are equal; the path must compare
    // by identity or a chain of look-alike methods reads as a cycle.
    private static Set<MethodDeclaration> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static Set<ClassOrInterfaceDeclaration> identityTypeSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private record Resolved(String fqcn, boolean ambiguous) {
    }

    // Java's scoping, reduced: a unique simple name is the type; otherwise a qualified reference, a single-type
    // import, or the same package decides; anything else is ambiguous. Not in the tree gives a null fqcn.
    private Resolved resolveType(String typeAsString, CompilationUnit cu) {
        String raw = typeAsString;
        int lt = raw.indexOf('<');
        if (lt >= 0) {
            raw = raw.substring(0, lt);
        }
        raw = raw.replace("[]", "").trim();
        String simple = lastSegment(raw);
        List<String> candidates = fqcnsBySimpleName.getOrDefault(simple, List.of());
        if (candidates.isEmpty()) {
            return new Resolved(null, false);
        }
        if (raw.contains(".")) {
            return new Resolved(candidates.contains(raw) ? raw : null, false);
        }
        if (candidates.size() == 1) {
            return new Resolved(candidates.get(0), false);
        }
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isAsterisk() && !imp.isStatic() && lastSegment(imp.getNameAsString()).equals(simple)) {
                String imported = imp.getNameAsString();
                return new Resolved(candidates.contains(imported) ? imported : null, false);
            }
        }
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        String samePackage = pkg.isEmpty() ? simple : pkg + "." + simple;
        if (candidates.contains(samePackage)) {
            return new Resolved(samePackage, false);
        }
        return new Resolved(null, true);
    }

    private static Finding worse(Finding a, Finding b) {
        return b.severity().ordinal() > a.severity().ordinal() ? b : a;
    }

    private static String simpleName(String typeAsString) {
        String raw = typeAsString;
        int lt = raw.indexOf('<');
        if (lt >= 0) {
            raw = raw.substring(0, lt);
        }
        return lastSegment(raw.replace("[]", ""));
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
