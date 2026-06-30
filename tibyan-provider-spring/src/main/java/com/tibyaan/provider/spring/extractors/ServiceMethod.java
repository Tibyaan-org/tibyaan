package com.tibyaan.provider.spring.extractors;

import java.util.List;

/**
 * One public method discovered on a {@code @Service} class (Volume 5 section 4.1).
 *
 * <p>This is a neutral, source-level fact: it records what the method looks like, with no
 * knowledge of entities or capabilities. {@link ServiceExtractor} produces a catalog of these,
 * and the provider matches read intents against the catalog to discover bindings. The parameter
 * types are kept as written in source so the matcher can map them through the same
 * Java-to-primitive mapping the entity fields use.
 *
 * @param serviceSimpleName    the owning service class's simple name, for example {@code ProductService}
 * @param methodName           the method name, for example {@code findById}
 * @param returnTypeSimpleName the return type's simple name, for example {@code Product}
 * @param parameterTypes       each parameter's type as written in source, in declaration order
 */
public record ServiceMethod(
        String serviceSimpleName,
        String methodName,
        String returnTypeSimpleName,
        List<String> parameterTypes) {

    public ServiceMethod {
        if (serviceSimpleName == null || serviceSimpleName.isBlank()) {
            throw new IllegalArgumentException("service method must name its service");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("service method must have a name");
        }
        parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
    }

    /** The binding string the runtime resolves, {@code <ServiceClassSimpleName>#<methodName>}. */
    public String toBinding() {
        return serviceSimpleName + "#" + methodName;
    }

    /** A readable signature for diagnostics, for example {@code ProductService#findById(Long)}. */
    public String signature() {
        return serviceSimpleName + "#" + methodName + "(" + String.join(", ", parameterTypes) + ")";
    }
}
