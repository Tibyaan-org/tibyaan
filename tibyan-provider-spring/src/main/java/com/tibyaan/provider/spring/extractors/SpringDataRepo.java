package com.tibyaan.provider.spring.extractors;

/**
 * A Spring Data repository declaration recognized in the scanned source (Volume 5 section 4.2,
 * condition 4a): an interface extending one of the recognized supertypes, carrying the entity and
 * id type arguments the framework contract defines.
 *
 * @param interfaceSimpleName the repository interface's simple name, for example {@code UnitJpaRepository}
 * @param entitySimpleName    the first type argument, the entity the repository is of, for example {@code Unit}
 * @param idTypeAsWritten     the second type argument as written, the id type, for example {@code Long}
 */
public record SpringDataRepo(String interfaceSimpleName, String entitySimpleName, String idTypeAsWritten) {
}
