package org.vedenemo.app;

import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.storage.memory.InMemoryModelStorage;

/**
 * Minimal application composition root.
 */
public final class VedenemoApp {

    private VedenemoApp() {
    }

    public static CommandExecutor createCommandExecutor() {
        return new CommandExecutor(new InMemoryModelStorage());
    }
}
