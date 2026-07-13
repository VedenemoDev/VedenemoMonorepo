package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.spi.storage.ModelStorage;
import org.vedenemo.core.session.Session;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes commands in the context of one active session.
 */
public final class CommandExecutor {

    private final ModelStorage modelStorage;
    private final ModelRegistry modelRegistry;
    private final Session session;

    public CommandExecutor(ModelStorage modelStorage, ModelRegistry modelRegistry, Session session) {
        this.modelStorage = Objects.requireNonNull(modelStorage, "modelStorage must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public void execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        apply(command);
        session.record(command);
    }

    public UndoResult undoLatest() {
        Optional<Command> latestCommand = session.latestCommand();
        if (latestCommand.isEmpty()) {
            return UndoResult.NOTHING_TO_UNDO;
        }
        UndoResult result = undo(latestCommand.orElseThrow());
        session.removeLatestCommand();
        return result;
    }

    private UndoResult undo(Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            apply(new DeleteEntityCommand(createEntityCommand.modelAzName(), createEntityCommand.entityAzName()));
            return UndoResult.UNDONE;
        }
        throw new IllegalStateException("command has no undo counterpart: " + command.getClass().getSimpleName());
    }

    private void apply(Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            applyCreateEntity(createEntityCommand);
        } else if (command instanceof DeleteEntityCommand deleteEntityCommand) {
            applyDeleteEntity(deleteEntityCommand);
        } else if (command instanceof NoOpCommand) {
            // NoOpCommand intentionally has no model-changing behavior.
        } else {
            throw new IllegalArgumentException("unsupported command: " + command.getClass().getSimpleName());
        }
    }

    private void applyCreateEntity(CreateEntityCommand command) {
        String selectedModelAzName = session.selectedModelAzName()
                .orElseThrow(() -> new IllegalStateException("no selected model"));
        if (!ModelRoot.uniquenessKey(selectedModelAzName).equals(ModelRoot.uniquenessKey(command.modelAzName()))) {
            throw new IllegalStateException("command target model is not selected");
        }
        ModelRoot modelRoot = modelRegistry.find(command.modelAzName())
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
        modelRoot.addEntity(new VEntity(command.entityAzName(), command.entityVisName(), modelRoot.version()));
    }

    private void applyDeleteEntity(DeleteEntityCommand command) {
        ModelRoot modelRoot = modelRegistry.find(command.modelAzName())
                .orElseThrow(() -> new IllegalStateException("selected model not found"));
        modelRoot.removeEntity(command.entityAzName())
                .orElseThrow(() -> new IllegalStateException("entity not found"));
    }

    public ModelStorage modelStorage() {
        return modelStorage;
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public Session session() {
        return session;
    }
}
