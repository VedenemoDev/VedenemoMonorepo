package org.vedenemo.web.api.resource;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;
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
                {"entityAzName":"1Customer","entityVisName":"Customer"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("azName must start with an ASCII letter"));
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
        assertTrue(response.body().contains("\"undoneCommand\":\"create-entity\""));
        assertTrue(response.body().contains("\"modelAzName\":\"Example_Model\""));
        assertTrue(response.body().contains("\"entityAzName\":\"Customer\""));
        assertTrue(modelRoot.entities().isEmpty());
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().commandHistory().isEmpty());
    }

    @Test
    void createAttributeCommandAddsAttributeToExistingEntity() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email","dataType":"text"}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"dataType\":\"TEXT\""));
        assertEquals("Email", modelRoot.entities().getFirst().attributes().getFirst().azName());
        assertEquals("1.0.0", modelRoot.entities().getFirst().attributes().getFirst().activeSince().toString());
        assertEquals(1, sessionManager.findSession(sessionId).orElseThrow().commandHistory().size());
    }

    @Test
    void createAttributeCommandDefaultsMissingDataTypeToText() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email"}
                """);

        assertEquals(200, response.statusCode());
        assertEquals(org.vedenemo.core.model.DataType.TEXT, modelRoot.entities().getFirst().attributes().getFirst().type());
    }

    @Test
    void createAttributeCommandRejectsMissingEntity() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Missing","attributeAzName":"Email","attributeVisName":"Email","dataType":"TEXT"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("entity not found"));
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().commandHistory().isEmpty());
    }

    @Test
    void createAttributeCommandRejectsDuplicateAttribute() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);
        post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email","dataType":"TEXT"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"email","attributeVisName":"Duplicate Email","dataType":"TEXT"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("attribute azName must be unique within VEntity"));
        assertEquals(1, sessionManager.findSession(sessionId).orElseThrow().commandHistory().size());
    }

    @Test
    void createAttributeCommandRejectsUnsupportedDataType() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email","dataType":"invalid"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("unsupported dataType"));
    }

    @Test
    void createAttributeCommandAcceptsIsoDateAndTimeDataTypes() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        assertEquals(200, post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"BirthDate","attributeVisName":"Birth Date","dataType":"DATE"}
                """).statusCode());
        assertEquals(200, post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"StartTime","attributeVisName":"Start Time","dataType":"time"}
                """).statusCode());
        assertEquals(200, post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"UpdatedAt","attributeVisName":"Updated At","dataType":"datetime"}
                """).statusCode());

        assertEquals(org.vedenemo.core.model.DataType.DATE, modelRoot.entities().getFirst().attributes().get(0).type());
        assertEquals(org.vedenemo.core.model.DataType.TIME, modelRoot.entities().getFirst().attributes().get(1).type());
        assertEquals(org.vedenemo.core.model.DataType.DATETIME, modelRoot.entities().getFirst().attributes().get(2).type());
    }

    @Test
    void undoCommandRemovesPreviouslyCreatedAttribute() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new org.vedenemo.core.model.VEntity("Customer", "Customer", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);
        post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email","dataType":"TEXT"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/undo");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"undoneCommand\":\"create-attribute\""));
        assertTrue(response.body().contains("\"modelAzName\":\"Example_Model\""));
        assertTrue(response.body().contains("\"entityAzName\":\"Customer\""));
        assertTrue(response.body().contains("\"attributeAzName\":\"Email\""));
        assertTrue(modelRoot.entities().getFirst().attributes().isEmpty());
        assertTrue(sessionManager.findSession(sessionId).orElseThrow().commandHistory().isEmpty());
    }

    @Test
    void createAssociationCommandAddsAssociationToSelectedModel() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new VEntity("Customer", "Customer", modelRoot.version()));
        modelRoot.addEntity(new VEntity("Order", "Order", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-association", """
                {"kind":"ownership","associationAzName":"Customer_Orders","associationVisName":"orders","sourceEntityAzName":"Customer","targetEntityAzName":"Order","cardinality":"0..*"}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"kind\":\"OWNERSHIP\""));
        assertEquals("Customer_Orders", modelRoot.associations().getFirst().azName());
        assertEquals("0..*", modelRoot.associations().getFirst().cardinality().toString());
        assertEquals(1, sessionManager.findSession(sessionId).orElseThrow().commandHistory().size());
    }

    @Test
    void createRelationCommandAddsRelationToSelectedModel() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new VEntity("Student", "Student", modelRoot.version()));
        modelRoot.addEntity(new VEntity("Course", "Course", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/create-association", """
                {"kind":"relation","associationAzName":"Student_Course","associationVisName":"enrollment","sourceEntityAzName":"Student","targetEntityAzName":"Course","sourceRoleName":"student","targetRoleName":"course","sourceCardinality":"0..*","targetCardinality":"1..*"}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"kind\":\"RELATION\""));
        assertTrue(response.body().contains("\"sourceRoleName\":\"student\""));
        assertTrue(response.body().contains("\"targetCardinality\":\"1..*\""));
        assertEquals("Student_Course", modelRoot.associations().getFirst().azName());
        assertEquals("student", modelRoot.associations().getFirst().sourceRoleName());
        assertEquals("course", modelRoot.associations().getFirst().targetRoleName());
        assertEquals("0..*", modelRoot.associations().getFirst().sourceCardinality().toString());
        assertEquals("1..*", modelRoot.associations().getFirst().targetCardinality().toString());
    }

    @Test
    void undoCommandRemovesPreviouslyCreatedAssociation() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new VEntity("Customer", "Customer", modelRoot.version()));
        modelRoot.addEntity(new VEntity("Order", "Order", modelRoot.version()));
        UUID sessionId = extractSessionId(post("/sessions/start").body());
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);
        post("/sessions/" + sessionId + "/commands/create-association", """
                {"kind":"reference","associationAzName":"Order_Customer","associationVisName":"customer","sourceEntityAzName":"Order","targetEntityAzName":"Customer","cardinality":"1"}
                """);

        HttpResponse<String> response = post("/sessions/" + sessionId + "/commands/undo");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"undoneCommand\":\"create-association\""));
        assertTrue(response.body().contains("\"associationAzName\":\"Order_Customer\""));
        assertTrue(modelRoot.associations().isEmpty());
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
