package org.vedenemo.web.api.resource;

import org.junit.jupiter.api.Test;
import org.vedenemo.app.VedenemoApp;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.core.spi.snapshot.SnapshotContent;
import org.vedenemo.core.spi.snapshot.SnapshotDescriptor;
import org.vedenemo.core.spi.snapshot.SnapshotStore;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void executesPingCommandThroughConsoleSession() throws Exception {
        Javalin app = VedenemoWebApi.create(testConfig(), new ModelRegistry());
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());
            HttpResponse<String> start = post(baseUri.resolve("/console/sessions"), "{}");
            String sessionId = extract(start.body(), "sessionId");

            HttpResponse<String> command = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"ping\"}"
            );

            assertEquals(201, start.statusCode());
            assertEquals(200, command.statusCode());
            assertTrue(command.body().contains("Backend responded OK."));
        } finally {
            app.stop();
        }
    }

    @Test
    void createsEntityThroughBrowserConsolePromptFlow() throws Exception {
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

            HttpResponse<String> add = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"add\"}"
            );
            HttpResponse<String> visName = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"Customer\"}"
            );
            HttpResponse<String> azName = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"\"}"
            );

            assertEquals(201, start.statusCode());
            assertEquals(200, add.statusCode());
            assertTrue(add.body().contains("\"prompt\":\"Entity visible name: \""));
            assertEquals(200, visName.statusCode());
            assertTrue(visName.body().contains("\"prompt\":\"Entity azName [Customer]: \""));
            assertEquals(200, azName.statusCode());
            assertTrue(azName.body().contains("Entity Customer added."));
            assertTrue(azName.body().contains("\"prompt\":\"VedenemoCli[Example_Model]>\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void startsConsoleSessionAttachedToConnectedModelAndReportsMissingCloudSnapshotStore() throws Exception {
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
                    "{\"command\":\"save smoke\"}"
            );

            assertEquals(201, start.statusCode());
            assertTrue(start.body().contains("\"prompt\":\"VedenemoCli[Example_Model]>\""));
            assertEquals(200, command.statusCode());
            assertTrue(command.body().contains("Cloud snapshot store is not configured."));
        } finally {
            app.stop();
        }
    }

    @Test
    void savesAndListsCloudSnapshotsThroughBrowserConsole() throws Exception {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        SessionManager sessionManager = VedenemoApp.createSessionManager(modelRegistry, commandJournal);
        modelRegistry.add(ModelRoot.create("Example_Model", "Example Model", "1.0.0"));
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();

        Javalin app = VedenemoWebApi.create(
                testConfig(),
                modelRegistry,
                sessionManager,
                commandJournal,
                Optional.of(snapshotStore),
                "dev",
                Clock.fixed(Instant.parse("2026-07-28T18:30:00Z"), ZoneOffset.UTC)
        );
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());
            HttpResponse<String> start = post(
                    baseUri.resolve("/console/sessions"),
                    "{\"connectedModelAzName\":\"Example_Model\"}"
            );
            String sessionId = extract(start.body(), "sessionId");

            HttpResponse<String> save = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"save first\"}"
            );
            HttpResponse<String> snapshots = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"snapshots\"}"
            );

            assertEquals(201, start.statusCode());
            assertEquals(200, save.statusCode());
            assertTrue(save.body().contains("Saved model Example_Model to cloud snapshot Example_Model/first.vdos."));
            assertEquals(200, snapshots.statusCode());
            assertTrue(snapshots.body().contains("Cloud snapshots:"));
            assertTrue(snapshots.body().contains("Example_Model/first.vdos"));
            assertTrue(snapshotStore.contentFor("Example_Model/first.vdos").contains("vedenemo-script 1"));
        } finally {
            app.stop();
        }
    }

    @Test
    void promptsForReplacementAzNameWhenBrowserConsoleCloudLoadConflicts() throws Exception {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        SessionManager sessionManager = VedenemoApp.createSessionManager(modelRegistry, commandJournal);
        modelRegistry.add(ModelRoot.create("Existing_Model", "Existing Model", "1.0.0"));
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        snapshotStore.put(
                new SnapshotDescriptor("Existing_Model/saved.vdos", "Existing_Model", "Existing Model", "1.0.0", 0, Instant.parse("2026-07-28T18:30:00Z")),
                """
                        vedenemo-script 1

                        model azName=Existing_Model visName="Existing Model" version=1.0.0

                        commands

                        snapshot
                        """
        );

        Javalin app = VedenemoWebApi.create(
                testConfig(),
                modelRegistry,
                sessionManager,
                commandJournal,
                Optional.of(snapshotStore),
                "dev",
                Clock.fixed(Instant.parse("2026-07-28T18:30:00Z"), ZoneOffset.UTC)
        );
        try {
            start(app);
            URI baseUri = URI.create("http://127.0.0.1:" + app.port());
            HttpResponse<String> start = post(baseUri.resolve("/console/sessions"), "{}");
            String sessionId = extract(start.body(), "sessionId");

            HttpResponse<String> load = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"load Existing_Model/saved.vdos\"}"
            );
            HttpResponse<String> rename = post(
                    baseUri.resolve("/console/sessions/" + sessionId + "/commands"),
                    "{\"command\":\"Replacement_Model\"}"
            );

            assertEquals(200, load.statusCode());
            assertTrue(load.body().contains("model load failed: model already exists: Existing_Model"));
            assertTrue(load.body().contains("New model azName for import, or blank to cancel: "));
            assertEquals(200, rename.statusCode());
            assertTrue(rename.body().contains("Loaded model Replacement_Model from cloud snapshot Existing_Model/saved.vdos."));
            assertTrue(rename.body().contains("\"attachedModelAzName\":\"Replacement_Model\""));
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

    private static final class InMemorySnapshotStore implements SnapshotStore {
        private final Map<String, SnapshotContent> snapshots = new LinkedHashMap<>();

        @Override
        public List<SnapshotDescriptor> listSnapshots(String scope) {
            return snapshots.values().stream()
                    .map(SnapshotContent::descriptor)
                    .toList();
        }

        @Override
        public Optional<SnapshotContent> readSnapshot(String scope, String snapshotKey) {
            return Optional.ofNullable(snapshots.get(snapshotKey));
        }

        @Override
        public SnapshotDescriptor writeSnapshot(
                String scope,
                String modelAzName,
                String snapshotName,
                String content,
                SnapshotDescriptor descriptor
        ) {
            String key = modelAzName + "/" + snapshotName + ".vdos";
            SnapshotDescriptor storedDescriptor = new SnapshotDescriptor(
                    key,
                    descriptor.modelAzName(),
                    descriptor.modelVisName(),
                    descriptor.modelVersion(),
                    descriptor.commandCount(),
                    descriptor.savedAt()
            );
            snapshots.put(key, new SnapshotContent(storedDescriptor, content));
            return storedDescriptor;
        }

        private void put(SnapshotDescriptor descriptor, String content) {
            snapshots.put(descriptor.key(), new SnapshotContent(descriptor, content));
        }

        private String contentFor(String key) {
            return snapshots.get(key).content();
        }
    }
}
