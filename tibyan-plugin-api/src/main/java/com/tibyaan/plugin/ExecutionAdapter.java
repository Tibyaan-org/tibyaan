package com.tibyaan.plugin;

import java.util.Map;

/**
 * How a capability is actually executed (Volume 3, Volume 12). The runtime owns the adapter
 * for its host: it resolves a capability's binding to real logic and invokes it.
 *
 * <p>This separation is what keeps backends honest. Generated code asks the adapter to invoke
 * a capability by reference; it never calls a bean or a service itself. Swapping the host
 * platform means swapping the adapter, nothing in the backend or the IR.
 */
public interface ExecutionAdapter {

    /**
     * Invokes the referenced capability with the given named arguments and returns its result.
     *
     * @param capability the capability to run, by reference
     * @param args       the named arguments, keyed as the capability's inputs are named
     * @return the result of the underlying logic
     */
    Object invoke(CapabilityRef capability, Map<String, Object> args);
}
