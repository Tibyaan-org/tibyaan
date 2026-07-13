package com.tibyaan.provider.spring.extractors;

/**
 * A Spring Data repository declaration recognized in the scanned source (Volume 5 section 4.2,
 * condition 4a): an interface extending one of the recognized supertypes, carrying the entity and
 * id type arguments the framework contract defines.
 *
 * <p>The entity is carried two ways. {@code entitySimpleName} is the first type argument's simple
 * name, used for the matcher's AST-level, simple-name reasoning and its diagnostics. {@code entityKey}
 * is that type argument resolved to a single in-tree entity's key (the FQCN) by the two-stage resolver
 * (Volume 5 section 4.12.4), used for identity: whether this repository is a repository of THIS entity
 * is a key comparison, never a simple-name one, so a colliding simple name binds to the right entity
 * or refuses. It is null when the type argument is not an in-tree {@code @Entity} or the resolver
 * cannot narrow it.
 *
 * @param interfaceSimpleName the repository interface's simple name, for example {@code UnitJpaRepository}
 * @param entitySimpleName    the first type argument's simple name, the entity the repository is of
 * @param entityKey           the first type argument resolved to an in-tree entity key, or null
 * @param idTypeAsWritten     the second type argument as written, the id type, for example {@code Long}
 */
public record SpringDataRepo(String interfaceSimpleName, String entitySimpleName, String entityKey,
                             String idTypeAsWritten) {
}
