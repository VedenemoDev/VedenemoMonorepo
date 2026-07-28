package org.vedenemo.web.api.console;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.ConsoleCapabilities;
import org.vedenemo.console.ConsoleSession;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.SessionClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WebConsoleSessionRegistry {

    private final SessionClient sessionClient;
    private final ModelClient modelClient;
    private final CommandClient commandClient;
    private final ConsoleCapabilities capabilities;
    private final Map<UUID, WebConsoleSession> sessions = new LinkedHashMap<>();

    public WebConsoleSessionRegistry(
            SessionClient sessionClient,
            ModelClient modelClient,
            CommandClient commandClient,
            ConsoleCapabilities capabilities
    ) {
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
        this.commandClient = Objects.requireNonNull(commandClient, "commandClient must not be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
    }

    public synchronized WebConsoleSession startSession(String connectedModelAzName) throws IOException, InterruptedException {
        UUID backendSessionId = sessionClient.startSession();
        ConsoleSession session = new ConsoleSession(
                backendSessionId,
                modelClient,
                sessionClient,
                commandClient,
                capabilities
        );
        if (connectedModelAzName != null && !connectedModelAzName.isBlank()) {
            session.attachInitialModel(connectedModelAzName);
        }
        WebConsoleSession webSession = new WebConsoleSession(UUID.randomUUID(), session);
        sessions.put(webSession.id(), webSession);
        return webSession;
    }

    public synchronized Optional<WebConsoleSession> findSession(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public synchronized boolean endSession(UUID sessionId) throws IOException, InterruptedException {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        WebConsoleSession removed = sessions.remove(sessionId);
        if (removed == null) {
            return false;
        }
        sessionClient.endSession(removed.consoleSession().backendSessionId());
        return true;
    }
}
