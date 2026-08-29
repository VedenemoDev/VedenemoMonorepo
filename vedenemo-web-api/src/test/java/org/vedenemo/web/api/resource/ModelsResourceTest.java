package org.vedenemo.web.api.resource;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;
import org.vedenemo.core.model.ValueSetEntry;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelsResourceTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private ModelRegistry modelRegistry;
    private Javalin app;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        int port = availablePort();
        WebApiConfig config = new WebApiConfig("127.0.0.1", port, Set.of("*"));
        modelRegistry = new ModelRegistry();
        app = VedenemoWebApi.create(config, modelRegistry);
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
    void addModelRootReturnsCreatedModelWithNormalizedVersion() throws Exception {
        HttpResponse<String> response = post("/models/add", """
                {"azName":"Example_Model","visName":"Example Model","version":"01.002.3"}
                """);

        assertEquals(201, response.statusCode());
        assertEquals("{\"azName\":\"Example_Model\",\"visName\":\"Example Model\",\"version\":\"1.2.3\"}", response.body());
    }

    @Test
    void listModelRootsIncludesAddedModelsInInsertionOrder() throws Exception {
        post("/models/add", """
                {"azName":"First","visName":"First Model","version":"1.0.0"}
                """);
        post("/models/add", """
                {"azName":"Second_Model","visName":"Second Model","version":"2.0.0"}
                """);

        HttpResponse<String> response = get("/models/list");

        assertEquals(200, response.statusCode());
        assertEquals("""
                [{"azName":"First","visName":"First Model","version":"1.0.0"},{"azName":"Second_Model","visName":"Second Model","version":"2.0.0"}]\
                """, response.body());
    }

    @Test
    void duplicateAzNameIsRejectedCaseInsensitively() throws Exception {
        post("/models/add", """
                {"azName":"Example","visName":"Example Model","version":"1.0.0"}
                """);

        HttpResponse<String> exactDuplicate = post("/models/add", """
                {"azName":"Example","visName":"Duplicate Model","version":"1.0.1"}
                """);
        HttpResponse<String> caseOnlyDuplicate = post("/models/add", """
                {"azName":"example","visName":"Case Duplicate Model","version":"1.0.2"}
                """);

        assertEquals(409, exactDuplicate.statusCode());
        assertTrue(exactDuplicate.body().contains("model root already exists"));
        assertEquals(409, caseOnlyDuplicate.statusCode());
        assertTrue(caseOnlyDuplicate.body().contains("model root already exists"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        assertEquals(400, post("/models/add", """
                {"azName":"1Example","visName":"Example Model","version":"1.0.0"}
                """).statusCode());
        assertEquals(400, post("/models/add", """
                {"azName":"Example","visName":"   ","version":"1.0.0"}
                """).statusCode());
        assertEquals(400, post("/models/add", """
                {"azName":"Example","visName":"Example Model","version":"1.x.0"}
                """).statusCode());
    }

    @Test
    void listEntitiesReturnsEntitiesInInsertionOrder() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new VEntity("Customer", "Customer", modelRoot.version()));
        modelRoot.addEntity(new VEntity("Order", "Order", modelRoot.version()));

        HttpResponse<String> response = get("/models/Example_Model/entities");

        assertEquals(200, response.statusCode());
        assertEquals("""
                [{"azName":"Customer","visName":"Customer","activeSince":"1.0.0","deprecatedSince":null,"retiredSince":null},{"azName":"Order","visName":"Order","activeSince":"1.0.0","deprecatedSince":null,"retiredSince":null}]\
                """, response.body());
    }

    @Test
    void listAttributesReturnsAttributesInInsertionOrderWithLifecycleFields() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        VEntity customer = modelRoot.addEntity(new VEntity("Customer", "Customer", modelRoot.version()));
        customer.addAttribute(new VAttribute("Email", "Email", DataType.TEXT, modelRoot.version()));
        customer.addAttribute(new VAttribute("Website", "Website", DataType.URL, modelRoot.version()));

        HttpResponse<String> response = get("/models/Example_Model/entities/Customer/attributes");

        assertEquals(200, response.statusCode());
        assertEquals("""
                [{"azName":"Email","visName":"Email","dataType":"TEXT","required":false,"valueSetAzName":null,"activeSince":"1.0.0","deprecatedSince":null,"retiredSince":null},{"azName":"Website","visName":"Website","dataType":"URL","required":false,"valueSetAzName":null,"activeSince":"1.0.0","deprecatedSince":null,"retiredSince":null}]\
                """, response.body());
    }

    @Test
    void listValueSetsReturnsModelLevelValueSets() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addValueSet(new ValueSet("TreeSpecies", DataType.TEXT, List.of(
                new ValueSetEntry("PINE", "Pine"),
                new ValueSetEntry("SPRUCE", "Spruce")
        )));

        HttpResponse<String> response = get("/models/Example_Model/value-sets");

        assertEquals(200, response.statusCode());
        assertEquals("""
                [{"azName":"TreeSpecies","dataType":"TEXT","entries":[{"technicalValue":"PINE","visName":"Pine"},{"technicalValue":"SPRUCE","visName":"Spruce"}]}]\
                """, response.body());
    }

    @Test
    void listAssociationsReturnsModelAndEntityScopedAssociations() throws Exception {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        modelRoot.addEntity(new VEntity("Customer", "Customer", modelRoot.version()));
        modelRoot.addEntity(new VEntity("Order", "Order", modelRoot.version()));
        modelRoot.addAssociation(new OwnershipAssociation(
                "Customer_Orders",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*"),
                modelRoot.version()
        ));

        HttpResponse<String> modelResponse = get("/models/Example_Model/associations");
        HttpResponse<String> entityResponse = get("/models/Example_Model/entities/Order/associations");

        assertEquals(200, modelResponse.statusCode());
        assertEquals(modelResponse.body(), entityResponse.body());
        assertEquals("""
                [{"azName":"Customer_Orders","visName":"orders","kind":"OWNERSHIP","sourceEntityAzName":"Customer","targetEntityAzName":"Order","cardinality":"0..*","sourceRoleName":null,"targetRoleName":null,"sourceCardinality":null,"targetCardinality":"0..*","activeSince":"1.0.0","deprecatedSince":null,"retiredSince":null}]\
                """, modelResponse.body());
    }

    @Test
    void exportScriptReturnsModelCommandsAndSnapshot() throws Exception {
        post("/models/add", """
                {"azName":"Example_Model","visName":"Example Model","version":"1.0.0"}
                """);
        String sessionId = startSession();
        put("/sessions/" + sessionId + "/selected-model", """
                {"azName":"Example_Model"}
                """);
        post("/sessions/" + sessionId + "/commands/create-entity", """
                {"entityAzName":"Customer","entityVisName":"Customer"}
                """);
        post("/sessions/" + sessionId + "/commands/create-attribute", """
                {"entityAzName":"Customer","attributeAzName":"Email","attributeVisName":"Email","dataType":"TEXT","required":true}
                """);

        HttpResponse<String> response = get("/models/Example_Model/script");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("vedenemo-script 1"));
        assertTrue(response.body().contains("create-entity model=Example_Model entity=Customer visName=\"Customer\" activeSince=1.0.0"));
        assertTrue(response.body().contains("attribute entity=Customer azName=Email visName=\"Email\" dataType=TEXT required=true activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
    }

    @Test
    void importScriptCreatesModelAndReturnsCommandCount() throws Exception {
        HttpResponse<String> response = postText("/models/script", """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Email visName="Email" dataType=TEXT activeSince=1.0.0 deprecatedSince=null
                """);

        assertEquals(201, response.statusCode());
        assertEquals("{\"modelAzName\":\"Example_Model\",\"commandCount\":2}", response.body());
        assertTrue(modelRegistry.find("Example_Model").isPresent());
    }

    @Test
    void duplicateScriptImportIsRejected() throws Exception {
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));

        HttpResponse<String> response = postText("/models/script", """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands

                snapshot
                """);

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("model already exists"));
    }

    @Test
    void modelEventsWebSocketReceivesModelChangeEvents() throws Exception {
        LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(baseUrl.replace("http://", "ws://") + "/models/events"), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messages.add(data.toString());
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .join();

        String connected = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(connected);
        assertTrue(connected.contains("\"type\":\"connected\""));

        post("/models/add", """
                {"azName":"Example_Model","visName":"Example Model","version":"1.0.0"}
                """);

        String event = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertTrue(event.contains("\"type\":\"model-changed\""));
        assertTrue(event.contains("\"modelAzName\":\"Example_Model\""));

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void corsPreflightAllowsWriteMethods() throws Exception {
        HttpResponse<String> response = options("/data/Example_Model/roots/00000000-0000-0000-0000-000000000000", "PUT");

        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertEquals("DELETE, GET, OPTIONS, POST, PUT", response.headers().firstValue("Access-Control-Allow-Methods").orElse(""));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("Content-Type"));
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postText(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
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

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> options(String path, String requestMethod) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", requestMethod)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String startSession() throws IOException, InterruptedException {
        HttpResponse<String> response = post("/sessions/start", "");
        return response.body().replace("{\"sessionId\":\"", "").replace("\"}", "");
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
