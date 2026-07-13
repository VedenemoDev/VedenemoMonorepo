package org.vedenemo.web.api.resource;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.ModelRoot;
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

    private ModelRegistry modelRegistry;
    private SessionManager sessionManager;
    private Javalin app;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        int port = availablePort();
        WebApiConfig config = new WebApiConfig("127.0.0.1", port, Set.of("*"));
        modelRegistry = new ModelRegistry();
        sessionManager = new SessionManager(new InMemoryModelStorage(), modelRegistry);
        app = VedenemoWebApi.create(config, modelRegistry, sessionManager);
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

    @Test
    void selectModelUpdatesBackendSession() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        assertEquals(204, response.statusCode());
        assertEquals("Example_Model", sessionManager.findSession(sessionId).orElseThrow().selectedModelAzName().orElseThrow());
    }

    @Test
    void clearSelectedModelUpdatesBackendSession() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = delete("/sessions/" + sessionId + "/selected-model");

        assertEquals(204, response.statusCode());
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().selectedModelAzName().isEmpty());
    }

    @Test
    void selectModelRejectsUnknownSession() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));

        HttpResponse<String> response = put("/sessions/" + UUID.randomUUID() + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("session not found"));
    }

    @Test
    void selectModelRejectsUnknownModel() throws Exception {
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Missing_Model"}
                """);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("model not found"));
    }

    @Test
    void selectModelRejectsInvalidModelName() throws Exception {
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"123_Invalid"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("azName must start with an ASCII letter"));
    }

    @Test
    void clearSelectedModelRejectsUnknownSession() throws Exception {
        HttpResponse<String> response = delete("/sessions/" + UUID.randomUUID() + "/selected-model");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("session not found"));
    }

    @Test
    void createEntityCommandAddsEntityToSelectedModel() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-entity", """
                {"entityAzName":"Customer","entityVisName":"Customer"}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"azName\":\"Customer\""));
        assertEquals("Customer", modelRoot.entities().getFirst().azName());
        assertEquals("1.0.0", modelRoot.entities().getFirst().activeSince().toString());
        assertEquals(1, sessionManager.findSession(sessionId).orElseThrow().commandHistory().size());
    }

    @Test
    void createEntityCommandRejectsUnknownSession() throws Exception {
        HttpResponse<String> response = post("/sessions/" + UUID.randomUUID() + "/commands/create-entity", """
                {"entityAzName":"Customer","entityVisName":"Customer"}
                """);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("session not found"));
    }

    @Test
    void createEntityCommandRejectsNoSelectedModel() throws Exception {
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-entity", """
                {"entityAzName":"Customer","entityVisName":"Customer"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("no selected model"));
    }

    @Test
    void createEntityCommandRejectsInvalidEntityInput() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-entity", """
                {"entityAzName":"Customer1","entityVisName":"Customer"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("azName must contain only ASCII letters and underscores"));
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().commandHistory().isEmpty());
    }

    @Test
    void undoCommandRemovesPreviouslyCreatedEntity() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);
        post("/sessions/" + sessionId + "/commands/create-entity", """
                {"entityAzName":"Customer","entityVisName":"Customer"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/undo");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"undone\""));
        assertTrue(modelRoot.entities().isEmpty());
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().commandHistory().isEmpty());
    }

    @Test
    void undoCommandReturnsNotModifiedWhenNothingCanBeUndone() throws Exception {
        UUID sessionId = extractSessionId(post("/sessions/start").body());

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/undo");

        assertEquals(304, response.statusCode());
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .DELETE()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
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
