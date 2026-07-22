package org.vedenemo.console;

public record ConsoleCapabilities(boolean localFileAccess) {

    public static ConsoleCapabilities terminal() {
        return new ConsoleCapabilities(true);
    }

    public static ConsoleCapabilities webConsole() {
        return new ConsoleCapabilities(false);
    }
}
