package com.tibyaan.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "version", description = "Print the tool and IR schema versions.")
final class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("tibyan " + TibyanCli.TOOL_VERSION);
        System.out.println("IR schema " + TibyanCli.IR_SCHEMA_VERSION);
        return 0;
    }
}
