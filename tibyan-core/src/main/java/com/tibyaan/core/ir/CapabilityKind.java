package com.tibyaan.core.ir;

/**
 * The kind of a capability (Volume 4 section 3.4).
 *
 * <p>The MVP uses {@link #READ} only. {@link #WRITE} and {@link #ACTION} are defined here
 * because the validation rules reference them (a read only capability must not be of kind
 * WRITE or ACTION), but no provider or backend produces them in the MVP.
 */
public enum CapabilityKind {

    /** Reads state and has no side effects. The only kind used in the MVP. */
    READ,
    /** Changes state. Out of MVP scope. */
    WRITE,
    /** Performs a side effecting operation that is not a simple write. Out of MVP scope. */
    ACTION
}
