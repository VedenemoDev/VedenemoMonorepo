package org.vedenemo.cli;

import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.storage.memory.InMemoryModelStorage;

/**
 * Minimal CLI entry point.
 */
public final class VedenemoCli {

    private VedenemoCli() {
    }

    public static void main(String[] args) {
        CommandExecutor executor = new CommandExecutor(new InMemoryModelStorage());
        System.out.println("Vedenemo CLI skeleton started. Executor: " + executor.getClass().getSimpleName());
    }
}
