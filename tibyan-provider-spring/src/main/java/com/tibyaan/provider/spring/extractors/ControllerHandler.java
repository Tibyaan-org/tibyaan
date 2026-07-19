package com.tibyaan.provider.spring.extractors;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.List;

/**
 * One GET-mapped handler on a concrete controller class, the neutral catalog entry the {@link
 * ControllerExtractor} produces (Volume 5 section 4.14.2, condition 6, the pooling decision 4.14.3). It
 * knows nothing about entities, services, or capabilities; the provider join (ControllerAuthority) ties
 * it to a discovered capability by resolving its delegation target, exactly as the provider joins the
 * entity and service extractors.
 *
 * @param controllerClass       the concrete {@code @RestController} (or {@code @Controller} returning
 *                              {@code ResponseEntity}) class the handler is declared on
 * @param implMethod            the handler method, whose body carries the delegation and whose position
 *                              carries the method-level security
 * @param mappingDecl           the declaration that carries the GET mapping and the parameter/return
 *                              shape (the overridden interface method in the split idiom, else implMethod)
 * @param getMappedSinglePlace  true when a GET mapping was found in exactly one place (condition 2); a
 *                              mapping found nowhere is not a handler at all, and one found in more than
 *                              one place is an ambiguous mapping source that does not anchor
 * @param bodyDelegations       every field-scoped call the body makes, anywhere, for the association
 *                              sweep (a handler whose body contains a call to a capability's binding is
 *                              associated with it, strict or not)
 * @param strictSingleStatement true when the body is EXACTLY {@code return <call>;} or {@code return
 *                              ResponseEntity.ok(<call>);} with a single verbatim-argument delegation
 *                              (condition 4), the shape a strict anchor requires
 * @param strictDelegation      the single delegation when {@code strictSingleStatement}, else null
 * @param effectiveDeclarations the effective method-security declaration per mechanism (most-specific
 *                              wins: method, then class, then interface method, then interface),
 *                              Volume 5 section 4.14.4
 */
record ControllerHandler(
        ClassOrInterfaceDeclaration controllerClass,
        MethodDeclaration implMethod,
        MethodDeclaration mappingDecl,
        boolean getMappedSinglePlace,
        List<Call> bodyDelegations,
        boolean strictSingleStatement,
        Call strictDelegation,
        List<SecurityDeclaration> effectiveDeclarations) {

    /**
     * One method call the controller body makes on one of its own fields, {@code field.method(args)}.
     * The provider resolves {@code field} to the backing {@code @Service} to form the binding string it
     * compares against a capability's binding.
     */
    record Call(String fieldName, String methodName, List<Expression> arguments) {
    }
}
