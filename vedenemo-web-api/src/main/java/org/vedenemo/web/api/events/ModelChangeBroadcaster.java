package org.vedenemo.web.api.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.router.JavalinDefaultRoutingApi;
import io.javalin.websocket.WsContext;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelChangeBroadcaster {

    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(JavalinDefaultRoutingApi routes) {
        routes.ws("/models/events", ws -> {
            ws.onConnect(context -> {
                clients.add(context);
                context.send(message("connected", null));
            });
            ws.onClose(context -> clients.remove(context));
            ws.onError(context -> clients.remove(context));
        });
    }

    public void broadcastModelChanged(String modelAzName) {
        broadcast(message("model-changed", Objects.requireNonNull(modelAzName, "modelAzName must not be null")));
    }

    private void broadcast(String message) {
        for (WsContext client : clients) {
            try {
                client.send(message);
            } catch (RuntimeException exception) {
                clients.remove(client);
            }
        }
    }

    private String message(String type, String modelAzName) {
        try {
            return objectMapper.writeValueAsString(new ModelChangeEvent(type, modelAzName, Instant.now().toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize model change event", exception);
        }
    }

    private record ModelChangeEvent(String type, String modelAzName, String occurredAt) {
    }
}
