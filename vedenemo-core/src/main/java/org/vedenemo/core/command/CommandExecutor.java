package org.vedenemo.core.command;

import org.vedenemo.core.spi.storage.ModelStorage;
import org.vedenemo.core.session.Session;

import java.util.Objects;

/**
 * Minimal placeholder executor.
 *
 * Real command execution behavior is intentionally out of scope for the first milestone.
 */
public final class CommandExecutor {

    private final ModelStorage modelStorage;
    private final Session session;

    public CommandExecutor(ModelStorage modelStorage, Session session) {
        this.modelStorage = Objects.requireNonNull(modelStorage, "modelStorage must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public void execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        session.record(command);
        // TODO: Implement real command execution later.
    }

    public ModelStorage modelStorage() {
        return modelStorage;
    }

    public Session session() {
        return session;
    }
}
