package org.vedenemo.app;

import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.storage.memory.InMemoryModelStorage;

/**
 * Minimal application composition root.
 */
public final class VedenemoApp {

    private VedenemoApp() {
    }

    public static CommandExecutor createCommandExecutor() {
        return createCommandExecutor(Session.create(), createModelRegistry());
    }

    public static CommandExecutor createCommandExecutor(Session session) {
        return createCommandExecutor(session, createModelRegistry());
    }

    public static CommandExecutor createCommandExecutor(Session session, ModelRegistry modelRegistry) {
        return new CommandExecutor(new InMemoryModelStorage(), modelRegistry, session);
    }

    public static ModelRegistry createModelRegistry() {
        return new ModelRegistry();
    }

    public static SessionManager createSessionManager() {
        return createSessionManager(createModelRegistry());
    }

    public static SessionManager createSessionManager(ModelRegistry modelRegistry) {
        return new SessionManager(new InMemoryModelStorage(), modelRegistry);
    }
}
