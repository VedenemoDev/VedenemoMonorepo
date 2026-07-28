package org.vedenemo.console;

public record ConsoleCapabilities(boolean localFileAccess, boolean cloudSnapshots) {

    public ConsoleCapabilities(boolean localFileAccess) {
        this(localFileAccess, false);
    }

    public static ConsoleCapabilities terminal() {
        return new ConsoleCapabilities(true, false);
    }

    public static ConsoleCapabilities webConsole() {
        return new ConsoleCapabilities(false, false);
    }

    public static ConsoleCapabilities webConsoleWithCloudSnapshots() {
        return new ConsoleCapabilities(false, true);
    }
}
