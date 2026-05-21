package org.vedenemo.core.command;

import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.Objects;

/**
 * Minimal placeholder executor.
 *
 * Real command execution behavior is intentionally out of scope for the first milestone.
 */
public final class CommandExecutor {

    private final ModelStorage modelStorage;

    public CommandExecutor(ModelStorage modelStorage) {
        this.modelStorage = Objects.requireNonNull(modelStorage, "modelStorage must not be null");
    }

    public void execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        // TODO: Implement real command execution later.
    }

    public ModelStorage modelStorage() {
        return modelStorage;
    }
}
