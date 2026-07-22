package org.vedenemo.console;

import java.util.List;
import java.util.Objects;

public record ConsoleCommandResult(Status status, List<String> outputLines) {

    public ConsoleCommandResult {
        Objects.requireNonNull(status, "status must not be null");
        outputLines = List.copyOf(Objects.requireNonNull(outputLines, "outputLines must not be null"));
    }

    public static ConsoleCommandResult ok(List<String> outputLines) {
        return new ConsoleCommandResult(Status.OK, outputLines);
    }

    public static ConsoleCommandResult error(List<String> outputLines) {
        return new ConsoleCommandResult(Status.ERROR, outputLines);
    }

    public enum Status {
        OK,
        ERROR
    }
}
