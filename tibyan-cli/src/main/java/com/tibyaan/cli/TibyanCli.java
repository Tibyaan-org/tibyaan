package com.tibyaan.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The {@code tibyan} command (Volume 11). It parses arguments and calls the pipeline; it holds no
 * compiler logic of its own. Exit codes follow Volume 11 section 4: 0 success, 1 usage error,
 * 2 validation failure, 3 provider or backend error.
 */
@Command(
        name = "tibyan",
        description = "Modernize a system: scan it into a Capability IR and generate an AI-operable layer.",
        mixinStandardHelpOptions = true,
        version = "tibyan " + TibyanCli.TOOL_VERSION,
        subcommands = {
                ScanCommand.class,
                GenerateCommand.class,
                ValidateCommand.class,
                AttestCommand.class,
                VersionCommand.class
        },
        exitCodeOnInvalidInput = 1,
        exitCodeOnExecutionException = 3)
public final class TibyanCli implements Runnable {

    static final String TOOL_VERSION = "0.3.0";
    static final String IR_SCHEMA_VERSION = "0.1";

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TibyanCli()).execute(args);
        System.exit(exitCode);
    }
}
