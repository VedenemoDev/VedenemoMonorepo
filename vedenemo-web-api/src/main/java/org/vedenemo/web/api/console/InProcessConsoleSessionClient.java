package org.vedenemo.web.api.console;

import org.vedenemo.console.SessionClient;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.session.SessionManager;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class InProcessConsoleSessionClient implements SessionClient {

    private final SessionManager sessionManager;
    private final ModelRegistry modelRegistry;

    InProcessConsoleSessionClient(SessionManager sessionManager, ModelRegistry modelRegistry) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
    }

    @Override
    public UUID startSession() {
        return sessionManager.startSession().id();
    }

    @Override
    public void endSession(UUID sessionId) {
        sessionManager.endSession(sessionId);
    }

    @Override
    public void selectModel(UUID sessionId, String azName) throws IOException {
        if (!modelRegistry.contains(azName)) {
            throw new IOException("model not found");
        }
        Optional<Session> session = sessionManager.findSession(sessionId);
        if (session.isEmpty()) {
            throw new IOException("session not found");
        }
        session.orElseThrow().selectModel(azName);
    }

    @Override
    public void clearSelectedModel(UUID sessionId) throws IOException {
        Optional<Session> session = sessionManager.findSession(sessionId);
        if (session.isEmpty()) {
            throw new IOException("session not found");
        }
        session.orElseThrow().clearSelectedModel();
    }
}
