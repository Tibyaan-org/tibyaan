package com.tibyaan.runtime.spring;

/**
 * One named, typed input of a tool, as read from the generated capabilities manifest. The type
 * is the IR canonical string, for example {@code LONG}.
 */
public record InputDescriptor(String name, String type) {
}
