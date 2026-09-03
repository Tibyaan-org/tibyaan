package com.tibyaan.cli;

import picocli.CommandLine;

/**
 * Supplies {@code --version} text at runtime.
 *
 * <p>picocli's {@code version} attribute takes a compile-time constant, which is exactly what the
 * tool must not use: a literal version outlives the release that set it. An {@code IVersionProvider}
 * is the supported way to resolve the string when the command runs instead.
 */
final class TibyanVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {"tibyan " + TibyanCli.TOOL_VERSION};
    }
}
