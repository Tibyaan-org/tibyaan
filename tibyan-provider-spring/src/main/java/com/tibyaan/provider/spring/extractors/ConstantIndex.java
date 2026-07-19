package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a {@code @Secured} value to its literal string, applying the constant rule (Volume 5
 * section 4.14.4): a value written as a name resolves by the section 4.12.4 scoping applied to static
 * members, and the resolved field must be a {@code public static final String} whose initializer is
 * EXACTLY one string literal. Anything else, an unresolvable name, an ambiguous wildcard, a non-literal
 * initializer (a concatenation, a method call), a not-in-tree declaring class, does not resolve, and
 * the caller marks the declaration unverifiable ({@code unresolvable-constant}). The compiled value is
 * always the exact literal the security layer matches; the tool never transforms it.
 *
 * <p>An inline string-literal value resolves to itself. The tool never resolves a constant inside a
 * SpEL string (that is {@link AuthorityCompiler}'s bounded template, which reads only single-quoted
 * literals), so this applies to {@code @Secured}'s Java-level annotation values only.
 */
final class ConstantIndex {

    // Declaring FQCN -> (field name -> the single string-literal value, or empty when the field exists
    // but its initializer is not exactly one string literal). A missing entry means no such field.
    private final Map<String, Map<String, Optional<String>>> literalsByFqcn;
    private final TypeScope typeScope;

    private ConstantIndex(Map<String, Map<String, Optional<String>>> literalsByFqcn, TypeScope typeScope) {
        this.literalsByFqcn = literalsByFqcn;
        this.typeScope = typeScope;
    }

    static ConstantIndex from(List<CompilationUnit> units, TypeScope typeScope) {
        Map<String, Map<String, Optional<String>>> literalsByFqcn = new HashMap<>();
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration decl : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                indexType(decl.getFullyQualifiedName().orElse(null), decl.getFields(), literalsByFqcn);
            }
            for (EnumDeclaration decl : unit.findAll(EnumDeclaration.class)) {
                indexType(decl.getFullyQualifiedName().orElse(null), decl.getFields(), literalsByFqcn);
            }
        }
        return new ConstantIndex(literalsByFqcn, typeScope);
    }

    private static void indexType(String fqcn, List<FieldDeclaration> declaredFields,
                                  Map<String, Map<String, Optional<String>>> into) {
        if (fqcn == null) {
            return;
        }
        Map<String, Optional<String>> fields = new HashMap<>();
        for (FieldDeclaration field : declaredFields) {
            indexField(field, fields);
        }
        if (!fields.isEmpty()) {
            into.put(fqcn, fields);
        }
    }

    // Only public static final String fields are eligible. A field of that shape whose initializer is a
    // bare string literal records the literal; any other initializer records "present but not a literal"
    // (Optional.empty), which the resolver reports as unresolvable rather than pretending it is absent.
    private static void indexField(FieldDeclaration field, Map<String, Optional<String>> into) {
        boolean shaped = field.hasModifier(Modifier.Keyword.PUBLIC)
                && field.hasModifier(Modifier.Keyword.STATIC)
                && field.hasModifier(Modifier.Keyword.FINAL)
                && "String".equals(lastSegment(field.getElementType().asString()));
        if (!shaped) {
            return;
        }
        for (VariableDeclarator var : field.getVariables()) {
            Optional<String> literal = var.getInitializer()
                    .filter(init -> init instanceof StringLiteralExpr)
                    .map(init -> ((StringLiteralExpr) init).asString());
            into.put(var.getNameAsString(), literal);
        }
    }

    /**
     * Resolve a {@code @Secured} value expression to its literal string, or empty when the value is
     * not a fully-resolvable literal (an inline literal resolves to itself; a name resolves through the
     * scoping; anything unresolvable or non-literal is empty).
     *
     * @param value         the annotation value expression, as written
     * @param cu            the compilation unit the annotation is written in (for its imports)
     * @param declaringType the type the annotation is on (for a same-class constant)
     */
    Optional<String> resolveLiteral(Expression value, CompilationUnit cu,
                                    ClassOrInterfaceDeclaration declaringType) {
        if (value instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (value instanceof NameExpr name) {
            return resolveSimpleName(name.getNameAsString(), cu, declaringType);
        }
        if (value instanceof FieldAccessExpr access) {
            return typeScope.resolveFqcn(access.getScope().toString(), cu)
                    .flatMap(fqcn -> literalAt(fqcn, access.getNameAsString()));
        }
        return Optional.empty();
    }

    // A bare name: a static single-member import decides first; else a same-class constant; else exactly
    // one in-tree type reached by a static wildcard import that declares the field. Any tie or miss is
    // unresolvable (Volume 5 section 4.14.4), never a guess.
    private Optional<String> resolveSimpleName(String name, CompilationUnit cu,
                                               ClassOrInterfaceDeclaration declaringType) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic() && !imp.isAsterisk() && lastSegment(imp.getNameAsString()).equals(name)) {
                String holder = qualifier(imp.getNameAsString());
                return literalAt(holder, name);
            }
        }
        if (declaringType != null) {
            Optional<String> sameClass = declaringType.getFullyQualifiedName()
                    .flatMap(fqcn -> literalAt(fqcn, name));
            if (sameClass.isPresent()) {
                return sameClass;
            }
        }
        Set<String> viaWildcard = new LinkedHashSet<>();
        Optional<String> onlyLiteral = Optional.empty();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic() && imp.isAsterisk()) {
                String holder = imp.getNameAsString();
                Map<String, Optional<String>> fields = literalsByFqcn.get(holder);
                if (fields != null && fields.containsKey(name)) {
                    viaWildcard.add(holder);
                    onlyLiteral = fields.get(name);
                }
            }
        }
        return viaWildcard.size() == 1 ? onlyLiteral : Optional.empty();
    }

    private Optional<String> literalAt(String fqcn, String field) {
        Map<String, Optional<String>> fields = literalsByFqcn.get(fqcn);
        if (fields == null) {
            return Optional.empty();
        }
        return fields.getOrDefault(field, Optional.empty());
    }

    private static String qualifier(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(0, dot) : qualifiedName;
    }

    private static String lastSegment(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
