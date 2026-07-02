package com.tibyaan.core.ir;

/**
 * An opaque reference that the runtime resolves to real logic (Volume 4 section 3.4).
 *
 * <p>The binding names <em>what</em> to invoke, not <em>how</em>. The IR never holds a bean,
 * a method handle, or any framework detail. A runtime and its ExecutionAdapter resolve this
 * string to a real call. Keeping it opaque is what lets the IR stay neutral while still being
 * executable. An example value is {@code "inventory.ProductService#findById"}.
 *
 * @param value the opaque binding string, meaningful only to the runtime that resolves it
 */
public record BindingRef(String value) {

    public BindingRef {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("binding value must not be null or blank");
        }
    }
}
