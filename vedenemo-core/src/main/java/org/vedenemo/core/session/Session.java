package org.vedenemo.core.session;

import org.vedenemo.core.command.Command;
import org.vedenemo.core.model.ModelRoot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Session {

    private final UUID id;
    private final List<Command> commandHistory = new ArrayList<>();
    private String selectedModelAzName;

    public Session(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public static Session create() {
        return new Session(UUID.randomUUID());
    }

    public UUID id() {
        return id;
    }

    public synchronized void record(Command command) {
        commandHistory.add(Objects.requireNonNull(command, "command must not be null"));
    }

    public synchronized List<Command> commandHistory() {
        return List.copyOf(commandHistory);
    }

    public synchronized List<Command> commandHistoryForUndo() {
        ArrayList<Command> reversed = new ArrayList<>(commandHistory);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    public synchronized Optional<String> selectedModelAzName() {
        return Optional.ofNullable(selectedModelAzName);
    }

    public synchronized void selectModel(String azName) {
        ModelRoot.uniquenessKey(azName);
        selectedModelAzName = azName;
    }

    public synchronized void clearSelectedModel() {
        selectedModelAzName = null;
    }
}
