package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.console.ConsoleCommandResult;
import org.vedenemo.console.ConsoleSession;
import org.vedenemo.web.api.console.WebConsoleSession;
import org.vedenemo.web.api.console.WebConsoleSessionRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ConsoleResource {

    private final WebConsoleSessionRegistry consoleSessions;
    private final ObjectMapper objectMapper;

    public ConsoleResource(WebConsoleSessionRegistry consoleSessions) {
        this.consoleSessions = Objects.requireNonNull(consoleSessions, "consoleSessions must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.post("/console/sessions", context -> {
            StartConsoleSessionRequest request;
            try {
                request = context.body().isBlank()
                        ? new StartConsoleSessionRequest(null)
                        : objectMapper.readValue(context.body(), StartConsoleSessionRequest.class);
                WebConsoleSession session = consoleSessions.startSession(request.connectedModelAzName());
                writeJson(context, 201, ConsoleSessionResponse.from(session));
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            }
        });
        routes.post("/console/sessions/{sessionId}/commands", context -> {
            UUID sessionId = parseSessionId(context.pathParam("sessionId"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("console session id is invalid"));
                return;
            }
            Optional<WebConsoleSession> session = consoleSessions.findSession(sessionId);
            if (session.isEmpty()) {
                writeJson(context, 404, new ErrorResponse("console session not found"));
                return;
            }
            ExecuteCommandRequest request;
            try {
                request = objectMapper.readValue(context.body(), ExecuteCommandRequest.class);
            } catch (JsonProcessingException exception) {
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
                return;
            }
            ConsoleSession consoleSession = session.orElseThrow().consoleSession();
            ConsoleCommandResult result = consoleSession.execute(request.command());
            writeJson(context, 200, ConsoleCommandResponse.from(consoleSession, result));
        });
        routes.delete("/console/sessions/{sessionId}", context -> {
            UUID sessionId = parseSessionId(context.pathParam("sessionId"));
            if (sessionId == null) {
                writeJson(context, 400, new ErrorResponse("console session id is invalid"));
                return;
            }
            try {
                if (consoleSessions.endSession(sessionId)) {
                    context.status(204);
                } else {
                    writeJson(context, 404, new ErrorResponse("console session not found"));
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                writeJson(context, 400, new ErrorResponse(exception.getMessage()));
            }
        });
    }

    private void writeJson(io.javalin.http.Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private record StartConsoleSessionRequest(String connectedModelAzName) {
    }

    private record ExecuteCommandRequest(String command) {
    }

    private record ConsoleSessionResponse(
            String sessionId,
            String backendSessionId,
            String prompt,
            String attachedModelAzName
    ) {
        private static ConsoleSessionResponse from(WebConsoleSession webSession) {
            ConsoleSession session = webSession.consoleSession();
            return new ConsoleSessionResponse(
                    webSession.id().toString(),
                    session.backendSessionId().toString(),
                    session.prompt(),
                    session.attachedModelAzName().orElse(null)
            );
        }
    }

    private record ConsoleCommandResponse(
            String status,
            List<String> outputLines,
            String prompt,
            String attachedModelAzName
    ) {
        private static ConsoleCommandResponse from(ConsoleSession session, ConsoleCommandResult result) {
            return new ConsoleCommandResponse(
                    result.status().name().toLowerCase(),
                    result.outputLines(),
                    session.prompt(),
                    session.attachedModelAzName().orElse(null)
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
}
