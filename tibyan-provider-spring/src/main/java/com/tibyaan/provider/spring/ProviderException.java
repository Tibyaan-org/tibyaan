package com.tibyaan.provider.spring;

/**
 * Raised when the provider cannot produce a faithful IR from the source: a file fails to parse,
 * the source root is unreadable, or a Java type has no equivalent in the closed primitive set.
 *
 * <p>An unmapped type is surfaced here rather than guessed at or silently dropped (Volume 5
 * rule 3). The message names the offending field and type so the mapping can be extended
 * deliberately.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
