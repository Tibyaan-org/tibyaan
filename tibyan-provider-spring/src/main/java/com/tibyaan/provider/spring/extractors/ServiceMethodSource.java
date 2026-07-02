package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * A public {@code @Service} method with its source, for the body analysis the DTO-aware discovery
 * needs (Volume 5 section 4.2, conditions 4 to 9). The neutral {@link ServiceMethod} carries the
 * signature and the binding string; the declaration and owning class carry the body and the
 * service's fields, which the matcher reads and the neutral catalog deliberately omits.
 *
 * @param method      the neutral signature and binding
 * @param declaration the method's AST, for body analysis
 * @param service     the owning {@code @Service} class, for resolving its repository fields
 */
public record ServiceMethodSource(
        ServiceMethod method,
        MethodDeclaration declaration,
        ClassOrInterfaceDeclaration service) {
}
