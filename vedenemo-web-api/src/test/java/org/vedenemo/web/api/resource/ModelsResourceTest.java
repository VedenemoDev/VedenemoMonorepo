package org.vedenemo.web.api.resource;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                [{"azName":"Customer","visName":"Customer","activeSince":"1.0.0","deprecatedSince":null},{"azName":"Order","visName":"Order","activeSince":"1.0.0","deprecatedSince":null}]\
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
                [{"azName":"Email","visName":"Email","dataType":"TEXT","activeSince":"1.0.0","deprecatedSince":null},{"azName":"Website","visName":"Website","dataType":"URL","activeSince":"1.0.0","deprecatedSince":null}]\
                """, response.body());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
