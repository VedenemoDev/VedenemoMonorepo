package org.vedenemo.web.api.console;

import org.vedenemo.console.ConsoleSession;

import java.util.Objects;
import java.util.UUID;

public record WebConsoleSession(UUID id, ConsoleSession consoleSession) {

    public WebConsoleSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(consoleSession, "consoleSession must not be null");
    }
}
