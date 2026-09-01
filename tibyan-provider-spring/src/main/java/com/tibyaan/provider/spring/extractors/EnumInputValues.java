package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * STRUCTURAL PROVABILITY for an enum-typed capability input (Volume 5, section 4.16.4). The never-guess bar
 * applied to a value set: the tool carries a set it READ from a resolved in-tree enum declaration, or it
 * carries none. It never infers values from a parameter's name, from usage, from call sites, or from a
 * naming pattern.
 *
 * <p>The set carried is THE SET OF VALUES THE APPLICATION ACCEPTS ON THE WIRE (condition EI-3), which is
 * not always the constant names:
 *
 * <ul>
 *   <li>SHAPE (a), BARE ENUM: no {@code @JsonValue} anywhere. The wire form is the constant name, which is
 *       Jackson's default, and the carried set is the constant names in declaration order.</li>
 *   <li>SHAPE (b), VALUE-MAPPED ENUM: exactly one {@code @JsonValue} member, a no-argument accessor whose
 *       body is a single {@code return <field>;}, that field assigned in the single constructor from
 *       exactly one parameter, and every constant passing exactly one string literal in that position. The
 *       carried set is those literals, in declaration order.</li>
 *   <li>SHAPE (c), ANYTHING ELSE: REFUSE to constrain, with a located reason. The tool does not interpret;
 *       it reads or it declines.</li>
 * </ul>
 *
 * <p>THE CONSTRUCTOR-LITERAL TRAP, which this class exists to not fall into. A hand-written enum may
 * declare {@code DRAFT("Draft"), ACTIVE("Active")} with a Lombok {@code @Getter} and NO {@code @JsonValue}.
 * Those literals are decorative labels; Jackson serializes the constant NAME. A rule that read constructor
 * literals whenever it saw them would advertise values the application rejects, which is the MIS-CONSTRAINED
 * breach (section 4.16.7). Shape (b) is therefore conditioned on {@code @JsonValue} proving the field IS
 * the wire form, never on the mere presence of a constructor literal.
 *
 * <p>The governing bias, as everywhere else in this tool: advertising a SUBSET of the accepted values is
 * safe, advertising a SUPERSET is a breach, so an unreadable or ambiguous declaration produces no set at
 * all rather than a guessed one.
 */
public final class EnumInputValues {

    private static final String JSON_VALUE = "JsonValue";

    private final TypeReferenceResolver resolver;
    private final List<CompilationUnit> units;

    public EnumInputValues(TypeReferenceResolver resolver, List<CompilationUnit> units) {
        this.resolver = resolver;
        this.units = units;
    }

    /**
     * The outcome for one parameter. Exactly three states, and no fourth: the type is not an in-tree enum
     * at all (so this rule has nothing to say and the caller maps the type as it always did); the enum
     * resolved and its wire values were read; or the enum resolved and the tool DECLINED to read it, which
     * is a refusal carrying its reason.
     *
     * @param enumType whether the declared type resolved to an in-tree enum declaration
     * @param values   the wire values in declaration order, when read
     * @param refusal  the located reason the tool declined to constrain, when it declined
     */
    public record Outcome(boolean enumType, List<String> values, String refusal) {

        public boolean isProvable() {
            return enumType && refusal == null;
        }

        public boolean isRefused() {
            return enumType && refusal != null;
        }

        public static Outcome notAnEnum() {
            return new Outcome(false, List.of(), null);
        }

        static Outcome provable(List<String> values) {
            return new Outcome(true, List.copyOf(values), null);
        }

        static Outcome refused(String reason) {
            return new Outcome(true, List.of(), reason);
        }
    }

    /**
     * Resolves a declared parameter type against the compilation unit it was written in and, when it is an
     * in-tree enum, reads the closed set of wire values it accepts.
     */
    public Outcome resolve(String declaredType, CompilationUnit cu) {
        // EI-1, IN-TREE RESOLUTION: the same two-stage key resolution E3 already performs for enum FIELDS.
        // A type that resolves to no in-tree declaration, or to a non-enum, is not this rule's business.
        Optional<EnumDeclaration> declaration = resolveEnum(declaredType, cu);
        if (declaration.isEmpty()) {
            return Outcome.notAnEnum();
        }
        EnumDeclaration decl = declaration.get();

        List<MethodDeclaration> jsonValueMembers = new ArrayList<>();
        for (MethodDeclaration method : decl.getMethods()) {
            if (method.getAnnotationByName(JSON_VALUE).isPresent()) {
                jsonValueMembers.add(method);
            }
        }

        // Shape (a), BARE ENUM: no @JsonValue anywhere, so the wire form is the constant name.
        if (jsonValueMembers.isEmpty()) {
            return Outcome.provable(constantNames(decl));
        }
        // Shape (c): more than one @JsonValue. Which one is the wire form is not readable.
        if (jsonValueMembers.size() > 1) {
            return Outcome.refused("enum '" + decl.getNameAsString() + "' declares " + jsonValueMembers.size()
                    + " @JsonValue members, so which one is the wire form is not readable "
                    + "(section 4.16.4, EI-3 shape c)");
        }
        return valueMapped(decl, jsonValueMembers.get(0));
    }

    // Shape (b): the single @JsonValue accessor must be a no-argument getter whose body is exactly one
    // return of a field, that field must be assigned in the single constructor from exactly one parameter,
    // and every constant must pass exactly one string literal in that position. Each of those is a
    // condition rather than a preference: failing any of them means the tool cannot READ the wire values,
    // and it declines rather than approximating them.
    private Outcome valueMapped(EnumDeclaration decl, MethodDeclaration accessor) {
        String name = decl.getNameAsString();
        if (!accessor.getParameters().isEmpty()) {
            return Outcome.refused("enum '" + name + "' annotates @JsonValue on '"
                    + accessor.getNameAsString() + "', which takes arguments; only a no-argument accessor "
                    + "names the wire form (section 4.16.4, EI-3 shape c)");
        }
        Optional<String> field = returnedFieldName(accessor);
        if (field.isEmpty()) {
            return Outcome.refused("enum '" + name + "' annotates @JsonValue on '"
                    + accessor.getNameAsString() + "', whose body is not a single 'return <field>;'; a "
                    + "method with logic is not readable as the wire form (section 4.16.4, EI-3 shape c)");
        }

        List<ConstructorDeclaration> constructors = decl.getConstructors();
        if (constructors.size() != 1) {
            return Outcome.refused("enum '" + name + "' declares " + constructors.size()
                    + " constructors, so the wire-value position is not readable (section 4.16.4, "
                    + "EI-3 shape c)");
        }
        ConstructorDeclaration constructor = constructors.get(0);
        Optional<Integer> position = assignedParameterPosition(constructor, field.get());
        if (position.isEmpty()) {
            return Outcome.refused("enum '" + name + "' does not assign the @JsonValue field '"
                    + field.get() + "' from exactly one constructor parameter, so the wire-value position "
                    + "is not readable (section 4.16.4, EI-3 shape c)");
        }

        int index = position.get();
        List<String> values = new ArrayList<>();
        for (EnumConstantDeclaration constant : decl.getEntries()) {
            if (constant.getArguments().size() <= index) {
                return Outcome.refused("enum '" + name + "' constant '" + constant.getNameAsString()
                        + "' passes " + constant.getArguments().size() + " arguments, fewer than the "
                        + "wire-value position (section 4.16.4, EI-3 shape c)");
            }
            Expression argument = constant.getArgument(index);
            if (!(argument instanceof StringLiteralExpr literal)) {
                return Outcome.refused("enum '" + name + "' constant '" + constant.getNameAsString()
                        + "' passes a non-literal in the wire-value position, which the tool will not "
                        + "evaluate (section 4.16.4, EI-3 shape c)");
            }
            values.add(literal.asString());
        }
        if (values.isEmpty()) {
            return Outcome.refused("enum '" + name + "' declares no constants, so there is no closed set "
                    + "to carry (section 4.16.4, EI-2)");
        }
        return Outcome.provable(values);
    }

    // EI-2: the constants in DECLARATION ORDER. A constant with a body or constructor arguments still
    // contributes its name, so the set is deterministic whatever the constant's shape.
    private static List<String> constantNames(EnumDeclaration decl) {
        List<String> names = new ArrayList<>();
        for (EnumConstantDeclaration constant : decl.getEntries()) {
            names.add(constant.getNameAsString());
        }
        return names;
    }

    // The field a single-return accessor returns, or empty when the body is anything else.
    private static Optional<String> returnedFieldName(MethodDeclaration accessor) {
        return accessor.getBody()
                .filter(body -> body.getStatements().size() == 1)
                .map(body -> body.getStatement(0))
                .filter(Statement::isReturnStmt)
                .flatMap(statement -> statement.asReturnStmt().getExpression())
                .flatMap(EnumInputValues::fieldNameOf);
    }

    private static Optional<String> fieldNameOf(Expression expression) {
        if (expression instanceof NameExpr name) {
            return Optional.of(name.getNameAsString());
        }
        if (expression.isFieldAccessExpr() && expression.asFieldAccessExpr().getScope().isThisExpr()) {
            return Optional.of(expression.asFieldAccessExpr().getNameAsString());
        }
        return Optional.empty();
    }

    // The constructor parameter position the field is assigned from, when exactly one assignment does it.
    private static Optional<Integer> assignedParameterPosition(ConstructorDeclaration constructor,
                                                               String field) {
        Integer found = null;
        for (AssignExpr assignment : constructor.findAll(AssignExpr.class)) {
            Optional<String> target = fieldNameOf(assignment.getTarget());
            if (target.isEmpty() || !target.get().equals(field)) {
                continue;
            }
            Optional<String> source = fieldNameOf(assignment.getValue());
            if (source.isEmpty()) {
                return Optional.empty();
            }
            int index = -1;
            for (int i = 0; i < constructor.getParameters().size(); i++) {
                if (constructor.getParameter(i).getNameAsString().equals(source.get())) {
                    index = i;
                    break;
                }
            }
            if (index < 0 || found != null) {
                return Optional.empty();
            }
            found = index;
        }
        return Optional.ofNullable(found);
    }

    // EI-1: resolve the written type to exactly one in-tree ENUM declaration, by the section 4.12.4 key
    // resolver, so a colliding simple name resolves to the right declaration or to none.
    private Optional<EnumDeclaration> resolveEnum(String declaredType, CompilationUnit cu) {
        TypeReferenceResolver.Resolution resolution = resolver.resolve(declaredType, cu);
        if (!resolution.isResolved() || resolution.kind() != TypeReferenceResolver.Kind.ENUM) {
            return Optional.empty();
        }
        String fqcn = resolution.fqcn();
        for (CompilationUnit unit : units) {
            for (BodyDeclaration<?> declaration : unit.findAll(EnumDeclaration.class)) {
                EnumDeclaration decl = (EnumDeclaration) declaration;
                if (decl.getFullyQualifiedName().map(fqcn::equals).orElse(false)) {
                    return Optional.of(decl);
                }
            }
        }
        return Optional.empty();
    }
}
