package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.tibyaan.provider.spring.extractors.ControllerHandler.Call;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads concrete controller classes and catalogs their GET handlers (Volume 5 section 4.14, phase
 * Controller-Authority; the placeholder since P1 is now alive). This is a neutral catalog producer,
 * symmetrical with the entity and service extractors: it records what each GET handler declares (its
 * mapping evidence, its one-statement delegation, its effective method-security declarations) and knows
 * nothing about entities, services, or capabilities. The provider owns the join.
 *
 * <p>It is deliberately a purpose-specific extractor (it does not implement the generic {@link
 * Extractor} contract, exactly as {@link ServiceExtractor} does not), because it contributes evidence
 * for a join, not an independent slice of the IR (section 4.14.3). Controller discovery grants NO
 * capability: a controller confirms and decorates a read the service rules proved; it never mints one.
 */
public final class ControllerExtractor {

    private static final String REST_CONTROLLER = "RestController";
    private static final String CONTROLLER = "Controller";
    private static final String RESPONSE_ENTITY = "ResponseEntity";

    /** The GET-handler catalog across every concrete controller class, in a deterministic order. */
    List<ControllerHandler> handlers(List<CompilationUnit> units, TypeScope typeScope) {
        List<ControllerHandler> handlers = new ArrayList<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.isInterface() || !isController(type)) {
                    continue;
                }
                boolean restController = hasClassAnnotation(type, REST_CONTROLLER);
                Set<String> fieldNames = fieldNames(type);
                List<InterfaceMethod> interfaceMethods = matchingInterfaceMethods(type, typeScope);
                for (MethodDeclaration method : type.getMethods()) {
                    handler(type, restController, fieldNames, method, interfaceMethods).ifPresent(handlers::add);
                }
            }
        }
        return handlers;
    }

    // A method is a GET handler when a GET mapping is found in exactly one place (condition 2), the
    // class is a @RestController or the mapping declaration returns ResponseEntity (condition 1), and its
    // body and security can be read. A method with no GET mapping is not a handler at all; an ambiguous
    // mapping (found in more than one place) does not anchor and is not cataloged (it cannot decorate).
    private Optional<ControllerHandler> handler(ClassOrInterfaceDeclaration controller, boolean rest,
                                                Set<String> fieldNames, MethodDeclaration implMethod,
                                                List<InterfaceMethod> interfaceMethods) {
        if (!implMethod.isPublic()) {
            return Optional.empty();
        }
        List<InterfaceMethod> overridden = overriddenBy(implMethod, interfaceMethods);

        // Condition 2: count the GET-mapping sources across the impl method and the overridden interface
        // method(s). Zero is not a handler; more than one is an ambiguous mapping source and refuses.
        List<MethodDeclaration> getSources = new ArrayList<>();
        if (isGetSource(implMethod)) {
            getSources.add(implMethod);
        }
        for (InterfaceMethod im : overridden) {
            if (isGetSource(im.method())) {
                getSources.add(im.method());
            }
        }
        if (getSources.size() != 1) {
            return Optional.empty();
        }
        MethodDeclaration mappingDecl = getSources.get(0);

        // Condition 1: a plain @Controller handler anchors only when it returns ResponseEntity (the
        // framework contract that makes the return value the response body); a @RestController's methods
        // always do. An explicit @ResponseBody on a non-ResponseEntity return is a named deferral. The
        // return type carries generics (ResponseEntity<Depot>), so it is compared by its raw name.
        if (!rest && !RESPONSE_ENTITY.equals(rawTypeName(mappingDecl.getType()))) {
            return Optional.empty();
        }

        BodyShape body = analyzeBody(implMethod, fieldNames);
        List<SecurityDeclaration> declarations = effectiveDeclarations(controller, implMethod, overridden);
        return Optional.of(new ControllerHandler(controller, implMethod, mappingDecl, true,
                body.delegations(), body.strict(), body.strictDelegation(), declarations));
    }

    // ---------- body analysis (conditions 4 and 5) ----------

    private record BodyShape(List<Call> delegations, boolean strict, Call strictDelegation) {
    }

    // The body's field-scoped calls (for the association sweep) and whether the body is EXACTLY the
    // one-statement delegation shape a strict anchor requires: return <call>; or return
    // ResponseEntity.ok(<call>); where <call> is a single field method call with verbatim name arguments.
    private BodyShape analyzeBody(MethodDeclaration method, Set<String> fieldNames) {
        Optional<BlockStmt> body = method.getBody();
        if (body.isEmpty()) {
            return new BodyShape(List.of(), false, null);
        }
        List<Call> delegations = new ArrayList<>();
        for (MethodCallExpr call : body.get().findAll(MethodCallExpr.class)) {
            fieldCall(call, fieldNames).ifPresent(delegations::add);
        }

        boolean strict = false;
        Call strictDelegation = null;
        List<Statement> statements = body.get().getStatements();
        if (statements.size() == 1 && statements.get(0) instanceof ReturnStmt ret
                && ret.getExpression().isPresent()) {
            Expression delegate = unwrapResponseEntityOk(ret.getExpression().get());
            Optional<Call> strictCall = strictFieldCall(delegate, fieldNames);
            if (strictCall.isPresent()) {
                strict = true;
                strictDelegation = strictCall.get();
            }
        }
        return new BodyShape(delegations, strict, strictDelegation);
    }

    // ResponseEntity.ok(x) unwraps to x (a recognized framework envelope, condition 4); anything else is
    // returned as-is for the strict check to accept or reject.
    private static Expression unwrapResponseEntityOk(Expression expression) {
        if (expression instanceof MethodCallExpr call
                && call.getScope().map(scope -> RESPONSE_ENTITY.equals(scope.toString())).orElse(false)
                && call.getNameAsString().equals("ok")
                && call.getArguments().size() == 1) {
            return call.getArgument(0);
        }
        return expression;
    }

    // A strict delegation: a single field method call whose arguments are all bare names (the verbatim
    // parameter forwarding condition 5 requires). A composed or nested argument (a call, an expression
    // over the parameter) disqualifies, so return service.map(service.findById(id)) is not strict.
    private static Optional<Call> strictFieldCall(Expression expression, Set<String> fieldNames) {
        Optional<Call> call = expression instanceof MethodCallExpr mce
                ? fieldCall(mce, fieldNames)
                : Optional.empty();
        if (call.isEmpty()) {
            return Optional.empty();
        }
        for (Expression argument : call.get().arguments()) {
            if (!(argument instanceof NameExpr)) {
                return Optional.empty();
            }
        }
        return call;
    }

    // A method call whose receiver is one of the controller's own fields, field.method(args) or
    // this.field.method(args), else empty (a call on a local, a type, or a chained result is not a
    // field delegation).
    private static Optional<Call> fieldCall(MethodCallExpr call, Set<String> fieldNames) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return Optional.empty();
        }
        String fieldName = fieldName(scope.get(), fieldNames);
        if (fieldName == null) {
            return Optional.empty();
        }
        return Optional.of(new Call(fieldName, call.getNameAsString(), new ArrayList<>(call.getArguments())));
    }

    private static String fieldName(Expression scope, Set<String> fieldNames) {
        if (scope instanceof NameExpr name && fieldNames.contains(name.getNameAsString())) {
            return name.getNameAsString();
        }
        if (scope instanceof FieldAccessExpr access
                && access.getScope().isThisExpr()
                && fieldNames.contains(access.getNameAsString())) {
            return access.getNameAsString();
        }
        return null;
    }

    // ---------- effective security declarations (section 4.14.4, most-specific wins) ----------

    // For each mechanism, the single effective declaration by the framework's lookup: the impl method,
    // else the impl class, else the overridden interface method, else that interface. Class-level applies
    // to every handler unless a method overrides it; the split idiom (mapping and params on the interface,
    // body and security on the impl) resolves naturally.
    private static List<SecurityDeclaration> effectiveDeclarations(ClassOrInterfaceDeclaration controller,
                                                                   MethodDeclaration implMethod,
                                                                   List<InterfaceMethod> overridden) {
        List<SecurityDeclarations.AnnotatedNode> lookup = new ArrayList<>();
        lookup.add(SecurityDeclarations.AnnotatedNode.of(implMethod));
        lookup.add(SecurityDeclarations.AnnotatedNode.of(controller));
        for (InterfaceMethod im : overridden) {
            lookup.add(SecurityDeclarations.AnnotatedNode.of(im.method()));
            lookup.add(SecurityDeclarations.AnnotatedNode.of(im.owner()));
        }
        return SecurityDeclarations.effective(lookup);
    }

    // ---------- interface resolution (the split-idiom lookup) ----------

    private record InterfaceMethod(ClassOrInterfaceDeclaration owner, MethodDeclaration method) {
    }

    // Every method on an in-tree interface the controller implements, indexed for the override match.
    private List<InterfaceMethod> matchingInterfaceMethods(ClassOrInterfaceDeclaration controller,
                                                           TypeScope typeScope) {
        List<InterfaceMethod> methods = new ArrayList<>();
        controller.getImplementedTypes().forEach(implemented ->
                typeScope.resolveFqcn(implemented.getNameWithScope(), owningUnit(controller))
                        .flatMap(typeScope::classOrInterface)
                        .filter(ClassOrInterfaceDeclaration::isInterface)
                        .ifPresent(iface -> iface.getMethods()
                                .forEach(method -> methods.add(new InterfaceMethod(iface, method)))));
        return methods;
    }

    // The interface methods the impl method overrides, matched by name and parameter count (the split
    // idiom overrides an interface method verbatim). Signature identity is enough for the benchmarks'
    // generated interfaces; a wider set would only add candidates the mapping/security lookup then reads.
    private static List<InterfaceMethod> overriddenBy(MethodDeclaration implMethod,
                                                      List<InterfaceMethod> interfaceMethods) {
        List<InterfaceMethod> matches = new ArrayList<>();
        for (InterfaceMethod candidate : interfaceMethods) {
            if (candidate.method().getNameAsString().equals(implMethod.getNameAsString())
                    && candidate.method().getParameters().size() == implMethod.getParameters().size()) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    // ---------- small shared helpers ----------

    private static boolean isController(ClassOrInterfaceDeclaration type) {
        return hasClassAnnotation(type, REST_CONTROLLER) || hasClassAnnotation(type, CONTROLLER);
    }

    // @GetMapping, or @RequestMapping whose method element is exactly RequestMethod.GET. A @RequestMapping
    // with no method element or more than one method is not GET evidence (condition 2).
    private static boolean isGetSource(MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String simple = simpleName(annotation.getNameAsString());
            if (simple.equals("GetMapping")) {
                return true;
            }
            if (simple.equals("RequestMapping") && isGetOnlyRequestMapping(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGetOnlyRequestMapping(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return false;
        }
        return normal.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("method"))
                .findFirst()
                .map(pair -> isExactlyGet(pair.getValue()))
                .orElse(false);
    }

    // The method element names exactly one HTTP method, GET: RequestMethod.GET, or { RequestMethod.GET }.
    private static boolean isExactlyGet(Expression value) {
        if (value instanceof ArrayInitializerExpr array) {
            return array.getValues().size() == 1 && namesGet(array.getValues().get(0));
        }
        return namesGet(value);
    }

    private static boolean namesGet(Expression value) {
        String text = value.toString();
        return text.equals("GET") || text.endsWith(".GET");
    }

    // The raw type name without generic arguments: a ClassOrInterfaceType gives its bare name directly
    // (ResponseEntity for ResponseEntity<Depot>), so condition 1 does not misread a generic return type.
    private static String rawTypeName(com.github.javaparser.ast.type.Type type) {
        if (type instanceof com.github.javaparser.ast.type.ClassOrInterfaceType coi) {
            return coi.getNameAsString();
        }
        return simpleName(type.asString());
    }

    private static Set<String> fieldNames(ClassOrInterfaceDeclaration type) {
        Set<String> names = new HashSet<>();
        type.getFields().forEach(field ->
                field.getVariables().forEach(var -> names.add(var.getNameAsString())));
        return names;
    }

    private static CompilationUnit owningUnit(ClassOrInterfaceDeclaration type) {
        return type.findCompilationUnit().orElseThrow(() ->
                new IllegalStateException("controller " + type.getNameAsString() + " has no compilation unit"));
    }

    private static boolean hasClassAnnotation(ClassOrInterfaceDeclaration type, String simpleName) {
        return type.getAnnotations().stream()
                .anyMatch(annotation -> simpleName.equals(simpleName(annotation.getNameAsString())));
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
