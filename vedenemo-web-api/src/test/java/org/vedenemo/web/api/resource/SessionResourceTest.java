package org.vedenemo.web.api.resource;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.storage.memory.InMemoryModelStorage;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionResourceTest {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\\{\"sessionId\":\"([^\"]+)\"}");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private SessionManager sessionManager;
    private Javalin app;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        int port = availablePort();
        WebApiConfig config = new WebApiConfig("127.0.0.1", port, Set.of("*"));
        sessionManager = new SessionManager(new InMemoryModelStorage());
        app = VedenemoWebApi.create(config, new ModelRegistry(), sessionManager);
        app.start(config.host(), config.port());
        baseUrl = "http://" + config.host() + ":" + config.port();
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void startSessionReturnsSessionIdAndCreatesBackendSession() throws Exception {
        HttpResponse<String> response = post("/sessions/start");

        UUID sessionId = extractSessionId(response.body());

        assertEquals(201, response.statusCode());
        assertTrue(sessionManager.findSession(sessionId).isPresent());
    }

    @Test
    void deleteSessionRemovesBackendSession() throws Exception {
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = delete("/sessions/" + sessionId);

        assertEquals(204, response.statusCode());
        assertTrue(sessionManager.findSession(sessionId).isEmpty());
    }

    @Test
    void deleteUnknownSessionReturnsNotFound() throws Exception {
        HttpResponse<String> response = delete("/sessions/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("session not found"));
    }

    @Test
    void deleteInvalidSessionIdReturnsBadRequest() throws Exception {
        HttpResponse<String> response = delete("/sessions/not-a-uuid");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("session uuid is invalid"));
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .DELETE()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static UUID extractSessionId(String body) {
        Matcher matcher = SESSION_ID_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new AssertionError("session response did not contain sessionId: " + body);
        }
        return UUID.fromString(matcher.group(1));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
