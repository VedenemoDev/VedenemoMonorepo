package org.vedenemo.web.api.console;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.UndoCommandResult;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.CreateAttributeCommand;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.UndoResult;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class InProcessConsoleCommandClient implements CommandClient {

    private final SessionManager sessionManager;
    private final ModelChangeBroadcaster modelChangeBroadcaster;

    InProcessConsoleCommandClient(SessionManager sessionManager, ModelChangeBroadcaster modelChangeBroadcaster) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.modelChangeBroadcaster = Objects.requireNonNull(modelChangeBroadcaster, "modelChangeBroadcaster must not be null");
    }

    @Override
    public void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new CreateEntityCommand(modelAzName, entityAzName, entityVisName));
        modelChangeBroadcaster.broadcastModelChanged(modelAzName);
    }

    @Override
    public void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType
    ) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new CreateAttributeCommand(
                modelAzName,
                entityAzName,
                attributeAzName,
                attributeVisName,
                parseDataType(dataType)
        ));
        modelChangeBroadcaster.broadcastModelChanged(modelAzName);
    }

    @Override
    public UndoCommandResult undo(UUID sessionId) throws IOException {
        UndoResult result = executor(sessionId).undoLatest();
        if (result.isNothingToUndo()) {
            return UndoCommandResult.NOTHING_TO_UNDO;
        }
        modelChangeBroadcaster.broadcastModelChanged(result.modelAzName());
        return UndoCommandResult.undone(
                result.undoneCommand(),
                result.modelAzName(),
                result.entityAzName(),
                result.attributeAzName()
        );
    }

    private CommandExecutor executor(UUID sessionId) throws IOException {
        return sessionManager.findExecutor(sessionId)
                .orElseThrow(() -> new IOException("session not found"));
    }

    private static String selectedModelAzName(CommandExecutor executor) throws IOException {
        Optional<String> selectedModelAzName = executor.session().selectedModelAzName();
        if (selectedModelAzName.isEmpty()) {
            throw new IOException("no selected model");
        }
        return selectedModelAzName.orElseThrow();
    }

    private static DataType parseDataType(String value) {
        if (value == null || value.isBlank()) {
            return DataType.TEXT;
        }
        return switch (value.trim().toLowerCase()) {
            case "text" -> DataType.TEXT;
            case "numeric", "number" -> DataType.NUMERIC;
            case "url" -> DataType.URL;
            case "data" -> DataType.DATA;
            default -> throw new IllegalArgumentException("unsupported dataType: " + value);
        };
    }
}
