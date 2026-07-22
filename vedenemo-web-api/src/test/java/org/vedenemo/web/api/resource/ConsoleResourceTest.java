package org.vedenemo.web.api.resource;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsoleResourceTest {

    @Test
    void createsConsoleSessionAndExecutesListCommand() throws Exception {
        ModelRegistry modelRegistry = new ModelRegistry();
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));

        Javalin app = VedenemoWebApi.create(testConfig(), modelRegistry);
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());
            HttpResponse<String> start = post(baseUri.resolve("/console/sessions"), "{}");
            String sessionId = extract(start.body(), "sessionId");
            String backendSessionId = extract(start.body(), "backendSessionId");

            HttpResponse<String> command = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"list\"}"
            );

            assertEquals(201, start.statusCode());
            assertTrue(!sessionId.equals(backendSessionId));
            assertEquals(200, command.statusCode());
            assertTrue(command.body().contains("Example Model (Example_Model) version 1.0.0"));
        } finally {
            app.stop();
        }
    }

    @Test
    void startsConsoleSessionAttachedToConnectedModelAndRejectsSaveAsText() throws Exception {
        ModelRegistry modelRegistry = new ModelRegistry();
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));

        Javalin app = VedenemoWebApi.create(testConfig(), modelRegistry);
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());
            HttpResponse<String> start = post(
                    baseUri.resolve("/console/sessions"),
                    "{\"connectedModelAzName\":\"Example_Model\"}"
            );
            String sessionId = extract(start.body(), "sessionId");

            HttpResponse<String> command = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"save\"}"
            );

            assertEquals(201, start.statusCode());
            assertTrue(start.body().contains("\"prompt\":\"VedenemoCli[Example_Model]>\""));
            assertEquals(200, command.statusCode());
            assertTrue(command.body().contains("Command 'save' is not supported in the web console because it requires local file access."));
        } finally {
            app.stop();
        }
    }

    @Test
    void invalidConsoleSessionReturnsNotFound() throws Exception {
        Javalin app = VedenemoWebApi.create(testConfig(), new ModelRegistry());
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());

            HttpResponse<String> command = post(
                    baseUri.resolve("/console/sessions/00000000-0000-0000-0000-000000000000/commands"),
                    "{\"command\":\"list\"}"
            );

            assertEquals(404, command.statusCode());
            assertTrue(command.body().contains("console session not found"));
        } finally {
            app.stop();
        }
    }

    private static HttpResponse<String> post(URI uri, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static WebApiConfig testConfig() {
        return new WebApiConfig("127.0.0.1", 0, Set.of("*"));
    }

    private static void start(Javalin app) {
        WebApiConfig config = testConfig();
        app.start(config.host(), config.port());
    }

    private static String extract(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("field not found: " + fieldName);
        }
        int valueStart = start + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
