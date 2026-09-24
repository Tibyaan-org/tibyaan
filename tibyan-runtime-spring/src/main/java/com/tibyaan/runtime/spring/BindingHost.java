package com.tibyaan.runtime.spring;

import java.util.Optional;

/**
 * Phase F9, hosted bindings (Volume 7 section 14, rule H1): the question the registrar asks the execution
 * adapter once at startup, before any tool registers: can this application execute this binding? The answer
 * is the same resolution the adapter performs at invocation (section 12.2, R2), run earlier, so a tool that
 * would fail its first call with "no bean of class" is not registered and is named instead.
 */
interface BindingHost {

    /**
     * Empty when exactly one bean hosts the binding's class; otherwise the reason it is not hosted (no bean of
     * that class, more than one bean of that class, or an unqualified binding), worded for the startup line.
     */
    Optional<String> unhostedReason(String binding);
}
