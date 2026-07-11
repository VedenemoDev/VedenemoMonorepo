package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.session.SessionManager;

import java.util.Objects;
import java.util.UUID;

public final class SessionResource {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public SessionResource(SessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
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
    }

    private void writeJson(io.javalin.http.Context context, int status, Object body) throws JsonProcessingException {
        context.status(status)
                .contentType("application/json")
                .result(objectMapper.writeValueAsString(body));
    }

    private record SessionResponse(String sessionId) {
    }

    private record ErrorResponse(String error) {
    }
}
