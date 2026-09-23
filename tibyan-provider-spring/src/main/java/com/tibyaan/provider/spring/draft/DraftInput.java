package com.tibyaan.provider.spring.draft;

/**
 * One parameter of a draft candidate, in declaration order.
 *
 * @param name     the parameter name as declared
 * @param javaType the parameter type exactly as written in source
 * @param type     the IR primitive the type mapper resolved it to, or {@code null} when it could not map
 *                 it (a collection, a DTO, an unmapped JDK type); a null here is what the reviewer and the
 *                 runtime binder will have to resolve before the draft can be served
 */
public record DraftInput(String name, String javaType, String type) {
}
