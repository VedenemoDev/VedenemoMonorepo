package org.vedenemo.web.api.console;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.UndoCommandResult;
import org.vedenemo.console.ValueSetEntryInput;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.CreateAssociationCommand;
import org.vedenemo.core.command.CreateAttributeCommand;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.CreateValueSetCommand;
import org.vedenemo.core.command.SetAttributeValueSetCommand;
import org.vedenemo.core.command.UndoResult;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ValueSetEntry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.io.IOException;
import java.util.List;
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
            String dataType,
            boolean required,
            String valueSetAzName
    ) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new CreateAttributeCommand(
                modelAzName,
                entityAzName,
                attributeAzName,
                attributeVisName,
                parseDataType(dataType),
                required,
                valueSetAzName
        ));
        modelChangeBroadcaster.broadcastModelChanged(modelAzName);
    }

    @Override
    public void createValueSet(UUID sessionId, String valueSetAzName, String dataType, List<ValueSetEntryInput> entries) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new CreateValueSetCommand(
                modelAzName,
                valueSetAzName,
                parseDataType(dataType),
                entries.stream()
                        .map(entry -> new ValueSetEntry(entry.technicalValue(), entry.visName()))
                        .toList()
        ));
        modelChangeBroadcaster.broadcastModelChanged(modelAzName);
    }

    @Override
    public void setAttributeValueSet(UUID sessionId, String entityAzName, String attributeAzName, String valueSetAzName) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new SetAttributeValueSetCommand(modelAzName, entityAzName, attributeAzName, valueSetAzName));
        modelChangeBroadcaster.broadcastModelChanged(modelAzName);
    }

    @Override
    public void createAssociation(
            UUID sessionId,
            String kind,
            String associationAzName,
            String associationVisName,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String sourceRoleName,
            String targetRoleName,
            String sourceCardinality,
            String targetCardinality
    ) throws IOException {
        CommandExecutor executor = executor(sessionId);
        String modelAzName = selectedModelAzName(executor);
        executor.execute(new CreateAssociationCommand(
                modelAzName,
                parseAssociationKind(kind),
                associationAzName,
                associationVisName,
                sourceEntityAzName,
                targetEntityAzName,
                cardinality == null || cardinality.isBlank() ? Cardinality.parse("1") : Cardinality.parse(cardinality),
                sourceRoleName,
                targetRoleName,
                parseOptionalCardinality(sourceCardinality),
                parseOptionalCardinality(targetCardinality)
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
                result.attributeAzName(),
                result.associationAzName()
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
            case "date" -> DataType.DATE;
            case "time" -> DataType.TIME;
            case "datetime" -> DataType.DATETIME;
            case "location" -> DataType.LOCATION;
            case "location_line", "location-line" -> DataType.LOCATION_LINE;
            case "location_area", "location-area" -> DataType.LOCATION_AREA;
            default -> throw new IllegalArgumentException("unsupported dataType: " + value);
        };
    }

    private static AssociationKind parseAssociationKind(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("association kind is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "ownership" -> AssociationKind.OWNERSHIP;
            case "reference" -> AssociationKind.REFERENCE;
            case "relation" -> AssociationKind.RELATION;
            default -> throw new IllegalArgumentException("unsupported association kind: " + value);
        };
    }

    private static Cardinality parseOptionalCardinality(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Cardinality.parse(value);
    }
}
