package org.vedenemo.core.session;

import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SessionManager {

    private final ModelStorage modelStorage;
    private final ModelRegistry modelRegistry;
    private final ModelCommandJournal commandJournal;
    private final Map<UUID, CommandExecutor> executorsBySessionId = new LinkedHashMap<>();

    public SessionManager(ModelStorage modelStorage) {
        this(modelStorage, new ModelRegistry(), new ModelCommandJournal());
    }

    public SessionManager(ModelStorage modelStorage, ModelRegistry modelRegistry) {
        this(modelStorage, modelRegistry, new ModelCommandJournal());
    }

    public SessionManager(ModelStorage modelStorage, ModelRegistry modelRegistry, ModelCommandJournal commandJournal) {
        this.modelStorage = Objects.requireNonNull(modelStorage, "modelStorage must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.commandJournal = Objects.requireNonNull(commandJournal, "commandJournal must not be null");
    }

    public synchronized Session startSession() {
        Session session = Session.create();
        executorsBySessionId.put(session.id(), new CommandExecutor(modelStorage, modelRegistry, commandJournal, session));
        return session;
    }

    public synchronized Optional<Session> findSession(UUID sessionId) {
        return findExecutor(sessionId).map(CommandExecutor::session);
    }

    public synchronized Optional<CommandExecutor> findExecutor(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return Optional.ofNullable(executorsBySessionId.get(sessionId));
    }

    public synchronized Optional<Session> endSession(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        CommandExecutor removed = executorsBySessionId.remove(sessionId);
        if (removed == null) {
            return Optional.empty();
        }
        return Optional.of(removed.session());
    }

    public synchronized int activeSessionCount() {
        return executorsBySessionId.size();
    }

    public ModelCommandJournal commandJournal() {
        return commandJournal;
    }
}
