package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
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
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public final class SessionResource {

    private final SessionManager sessionManager;
    private final ModelRegistry modelRegistry;
    private final ModelChangeBroadcaster modelChangeBroadcaster;
    private final ObjectMapper objectMapper;

    public SessionResource(SessionManager sessionManager, ModelRegistry modelRegistry) {
        this(sessionManager, modelRegistry, new ModelChangeBroadcaster());
    }

    public SessionResource(
            SessionManager sessionManager,
            ModelRegistry modelRegistry,
            ModelChangeBroadcaster modelChangeBroadcaster
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.modelChangeBroadcaster = Objects.requireNonNull(modelChangeBroadcaster, "modelChangeBroadcaster must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/sessions/start", context -> {
            Session session = sessionManager.startSession();
            writeJson(context, 201, new SessionResponse(session.id().toString()));
        });
        routes.delete("/sessions/{uuid}", context -> {
            UUID sessionId;
            try {
                sessionId = UUID.fromString(context.pathParam("uuid"));
            } catch (IllegalArgumentException exception) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            if (sessionManager.endSession(sessionId).isPresent()) {
                context.status(204);
            } else {
                writeJson(context, 404, new ErrorResponse("session not found"));
            }
        });
        routes.put("/sessions/{uuid}/selected-model", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            SelectModelRequest request;
            try {
                request = objectMapper.readValue(context.body(), SelectModelRequest.class);
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            if (request.azName() == null || request.azName().isBlank()) {
                writeJson(context, 400, new ErrorResponse("azName must not be blank"));
                return;
            }
            boolean modelExists;
            try {
                modelExists = modelRegistry.contains(request.azName());
            } catch (IllegalArgumentException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            if (!modelExists) {
                writeJson(context, 404, new ErrorResponse("model not found"));
                return;
            }
            Optional<Session> session = sessionManager.findSession(sessionId);
            if (session.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            session.orElseThrow().selectModel(request.azName());
            context.status(204);
        });
        routes.delete("/sessions/{uuid}/selected-model", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<Session> session = sessionManager.findSession(sessionId);
            if (session.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            session.orElseThrow().clearSelectedModel();
            context.status(204);
        });
        routes.post("/sessions/{uuid}/commands/create-entity", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            Optional<String> selectedModelAzName = executor.orElseThrow().session().selectedModelAzName();
            if (selectedModelAzName.isEmpty()) {
                writeJson(context, 400, new ErrorResponse("no selected model"));
                return;
            }
            CreateEntityRequest request;
            try {
                request = objectMapper.readValue(context.body(), CreateEntityRequest.class);
                executor.orElseThrow().execute(new CreateEntityCommand(
                        selectedModelAzName.orElseThrow(),
                        request.entityAzName(),
                        request.entityVisName()
                ));
                modelChangeBroadcaster.broadcastModelChanged(selectedModelAzName.orElseThrow());
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new EntityResponse(request.entityAzName(), request.entityVisName()));
        });
        routes.post("/sessions/{uuid}/commands/create-attribute", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            Optional<String> selectedModelAzName = executor.orElseThrow().session().selectedModelAzName();
            if (selectedModelAzName.isEmpty()) {
                writeJson(context, 400, new ErrorResponse("no selected model"));
                return;
            }
            CreateAttributeRequest request;
            DataType dataType;
            try {
                request = objectMapper.readValue(context.body(), CreateAttributeRequest.class);
                dataType = parseDataType(request.dataType());
                executor.orElseThrow().execute(new CreateAttributeCommand(
                        selectedModelAzName.orElseThrow(),
                        request.entityAzName(),
                        request.attributeAzName(),
                        request.attributeVisName(),
                        dataType,
                        request.required(),
                        request.valueSetAzName()
                ));
                modelChangeBroadcaster.broadcastModelChanged(selectedModelAzName.orElseThrow());
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new AttributeResponse(
                    request.attributeAzName(),
                    request.attributeVisName(),
                    dataType.name(),
                    request.required(),
                    request.valueSetAzName()
            ));
        });
        routes.post("/sessions/{uuid}/commands/create-value-set", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            Optional<String> selectedModelAzName = executor.orElseThrow().session().selectedModelAzName();
            if (selectedModelAzName.isEmpty()) {
                writeJson(context, 400, new ErrorResponse("no selected model"));
                return;
            }
            CreateValueSetRequest request;
            DataType dataType;
            try {
                request = objectMapper.readValue(context.body(), CreateValueSetRequest.class);
                dataType = parseDataType(request.dataType());
                executor.orElseThrow().execute(new CreateValueSetCommand(
                        selectedModelAzName.orElseThrow(),
                        request.valueSetAzName(),
                        dataType,
                        request.entries().stream()
                                .map(entry -> new ValueSetEntry(entry.technicalValue(), entry.visName()))
                                .toList()
                ));
                modelChangeBroadcaster.broadcastModelChanged(selectedModelAzName.orElseThrow());
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new ValueSetResponse(request.valueSetAzName(), dataType.name(), request.entries()));
        });
        routes.post("/sessions/{uuid}/commands/set-attribute-value-set", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            Optional<String> selectedModelAzName = executor.orElseThrow().session().selectedModelAzName();
            if (selectedModelAzName.isEmpty()) {
                writeJson(context, 400, new ErrorResponse("no selected model"));
                return;
            }
            SetAttributeValueSetRequest request;
            try {
                request = objectMapper.readValue(context.body(), SetAttributeValueSetRequest.class);
                executor.orElseThrow().execute(new SetAttributeValueSetCommand(
                        selectedModelAzName.orElseThrow(),
                        request.entityAzName(),
                        request.attributeAzName(),
                        request.valueSetAzName()
                ));
                modelChangeBroadcaster.broadcastModelChanged(selectedModelAzName.orElseThrow());
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new AttributeValueSetResponse(
                    request.entityAzName(),
                    request.attributeAzName(),
                    request.valueSetAzName()
            ));
        });
        routes.post("/sessions/{uuid}/commands/create-association", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            Optional<String> selectedModelAzName = executor.orElseThrow().session().selectedModelAzName();
            if (selectedModelAzName.isEmpty()) {
                writeJson(context, 400, new ErrorResponse("no selected model"));
                return;
            }
            CreateAssociationRequest request;
            AssociationKind kind;
            Cardinality cardinality;
            CreateAssociationCommand command;
            try {
                request = objectMapper.readValue(context.body(), CreateAssociationRequest.class);
                kind = parseAssociationKind(request.kind());
                cardinality = request.cardinality() == null || request.cardinality().isBlank()
                        ? Cardinality.parse("1")
                        : Cardinality.parse(request.cardinality());
                command = new CreateAssociationCommand(
                        selectedModelAzName.orElseThrow(),
                        kind,
                        request.associationAzName(),
                        request.associationVisName(),
                        request.sourceEntityAzName(),
                        request.targetEntityAzName(),
                        cardinality,
                        request.sourceRoleName(),
                        request.targetRoleName(),
                        parseOptionalCardinality(request.sourceCardinality()),
                        parseOptionalCardinality(request.targetCardinality())
                );
                executor.orElseThrow().execute(command);
                modelChangeBroadcaster.broadcastModelChanged(selectedModelAzName.orElseThrow());
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new AssociationResponse(
                    command.associationAzName(),
                    command.associationVisName(),
                    command.kind().name(),
                    command.sourceEntityAzName(),
                    command.targetEntityAzName(),
                    command.cardinality().toString(),
                    command.sourceRoleName(),
                    command.targetRoleName(),
                    command.sourceCardinality() == null ? null : command.sourceCardinality().toString(),
                    command.targetCardinality() == null ? null : command.targetCardinality().toString()
            ));
        });
        routes.post("/sessions/{uuid}/commands/undo", context -> {
            UUID sessionId = parseSessionId(context.pathParam("uuid"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("session uuid is invalid"));
                return;
            }
            Optional<CommandExecutor> executor = sessionManager.findExecutor(sessionId);
            if (executor.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("session not found"));
                return;
            }
            UndoResult result;
            try {
                result = executor.orElseThrow().undoLatest();
                if (result.isNothingToUndo()) {
                    context.status(304);
                    return;
                }
                modelChangeBroadcaster.broadcastModelChanged(result.modelAzName());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, UndoResponse.from(result));
        });
    }

    private void writeJson(io.javalin.http.Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private record SessionResponse(String sessionId) {
    }

    private record SelectModelRequest(String azName) {
    }

    private record CreateEntityRequest(String entityAzName, String entityVisName) {
    }

    private record CreateAttributeRequest(
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType,
            boolean required,
            String valueSetAzName
    ) {
    }

    private record CreateAssociationRequest(
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
    ) {
    }

    private record EntityResponse(String azName, String visName) {
    }

    private record ValueSetEntryRequest(Object technicalValue, String visName) {
    }

    private record CreateValueSetRequest(String valueSetAzName, String dataType, List<ValueSetEntryRequest> entries) {
    }

    private record SetAttributeValueSetRequest(String entityAzName, String attributeAzName, String valueSetAzName) {
    }

    private record AttributeResponse(String azName, String visName, String dataType, boolean required, String valueSetAzName) {
    }

    private record ValueSetResponse(String azName, String dataType, List<ValueSetEntryRequest> entries) {
    }

    private record AttributeValueSetResponse(String entityAzName, String attributeAzName, String valueSetAzName) {
    }

    private record AssociationResponse(
            String azName,
            String visName,
            String kind,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String sourceRoleName,
            String targetRoleName,
            String sourceCardinality,
            String targetCardinality
    ) {
    }

    private record UndoResponse(
            String status,
            String undoneCommand,
            String modelAzName,
            String entityAzName,
            String attributeAzName,
            String associationAzName
    ) {
        private static UndoResponse from(UndoResult result) {
            return new UndoResponse(
                    "undone",
                    result.undoneCommand(),
                    result.modelAzName(),
                    result.entityAzName(),
                    result.attributeAzName(),
                    result.associationAzName()
            );
        }
    }

    private record ErrorResponse(String error) {
    }

    private static UUID parseSessionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
