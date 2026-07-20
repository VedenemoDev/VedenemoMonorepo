package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.CreateAttributeCommand;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.UndoResult;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.session.SessionManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SessionResource {

    private final SessionManager sessionManager;
    private final ModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;

    public SessionResource(SessionManager sessionManager, ModelRegistry modelRegistry) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
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
                        dataType
                ));
            } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            writeJson(context, 200, new AttributeResponse(
                    request.attributeAzName(),
                    request.attributeVisName(),
                    dataType.name()
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
            String dataType
    ) {
    }

    private record EntityResponse(String azName, String visName) {
    }

    private record AttributeResponse(String azName, String visName, String dataType) {
    }

    private record UndoResponse(
            String status,
            String undoneCommand,
            String modelAzName,
            String entityAzName,
            String attributeAzName
    ) {
        private static UndoResponse from(UndoResult result) {
            return new UndoResponse(
                    "undone",
                    result.undoneCommand(),
                    result.modelAzName(),
                    result.entityAzName(),
                    result.attributeAzName()
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
            default -> throw new IllegalArgumentException("unsupported dataType: " + value);
        };
    }
}
