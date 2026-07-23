package org.vedenemo.console;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsoleSessionTest {

    @Test
    void listsModelsAndAttachesByNumber() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult listResult = session.execute("list");
        ConsoleCommandResult attachResult = session.execute("attach 1");

        assertEquals(ConsoleCommandResult.Status.OK, listResult.status());
        assertTrue(listResult.outputLines().contains("1. Example Model (Example_Model) version 1.0.0"));
        assertEquals(ConsoleCommandResult.Status.OK, attachResult.status());
        assertEquals("Example_Model", sessionClient.selectedModelAzName);
        assertEquals("VedenemoCli[Example_Model]>", session.prompt());
    }

    @Test
    void commandWordsAreCaseInsensitiveButAzNameParametersAreCaseSensitive() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult attachResult = session.execute("AtTaCh Example_Model");
        ConsoleCommandResult wrongCaseResult = session.execute("attach example_model");

        assertEquals(ConsoleCommandResult.Status.OK, attachResult.status());
        assertEquals("Example_Model", sessionClient.selectedModelAzName);
        assertEquals(ConsoleCommandResult.Status.OK, wrongCaseResult.status());
        assertEquals(List.of("No model found with azName example_model."), wrongCaseResult.outputLines());
    }

    @Test
    void rejectsSaveAndLoadAsPlainTextInWebConsole() {
        ConsoleSession session = new ConsoleSession(
                UUID.randomUUID(),
                new TestModelClient(),
                new TestSessionClient(),
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult saveResult = session.execute("save");
        ConsoleCommandResult loadResult = session.execute("load model.vdos");

        assertEquals(List.of("Command 'save' is not supported in the web console because it requires local file access."), saveResult.outputLines());
        assertEquals(List.of("Command 'load' is not supported in the web console because it requires local file access."), loadResult.outputLines());
    }

    @Test
    void pingChecksBackendThroughSharedModelClient() {
        TestModelClient modelClient = new TestModelClient();
        ConsoleSession session = new ConsoleSession(
                UUID.randomUUID(),
                modelClient,
                new TestSessionClient(),
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult result = session.execute("ping");

        assertEquals(ConsoleCommandResult.Status.OK, result.status());
        assertEquals(List.of("Backend responded OK."), result.outputLines());
        assertTrue(modelClient.pingCalled);
    }

    private static final class TestModelClient implements ModelClient {
        private final List<ModelSummary> models = new ArrayList<>();
        private boolean pingCalled;

        @Override
        public void ping() {
            pingCalled = true;
        }

        @Override
        public List<ModelSummary> listModels() {
            return List.copyOf(models);
        }

        @Override
        public ModelSummary addModel(String azName, String visName, String version) throws IOException {
            throw new IOException("not implemented");
        }

        @Override
        public List<EntitySummary> listEntities(String modelAzName) {
            return List.of();
        }

        @Override
        public List<AttributeSummary> listAttributes(String modelAzName, String entityAzName) {
            return List.of();
        }

        @Override
        public String exportScript(String modelAzName) throws IOException {
            throw new IOException("not implemented");
        }

        @Override
        public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException {
            throw new IOException("not implemented");
        }
    }

    private static final class TestSessionClient implements SessionClient {
        private final UUID sessionId = UUID.randomUUID();
        private String selectedModelAzName;

        @Override
        public UUID startSession() {
            return sessionId;
        }

        @Override
        public void endSession(UUID sessionId) {
        }

        @Override
        public void selectModel(UUID sessionId, String azName) {
            selectedModelAzName = azName;
        }

        @Override
        public void clearSelectedModel(UUID sessionId) {
            selectedModelAzName = null;
        }
    }

    private static final class TestCommandClient implements CommandClient {
        @Override
        public void createEntity(UUID sessionId, String entityAzName, String entityVisName) {
        }

        @Override
        public void createAttribute(
                UUID sessionId,
                String entityAzName,
                String attributeAzName,
                String attributeVisName,
                String dataType
        ) {
        }

        @Override
        public UndoCommandResult undo(UUID sessionId) {
            return UndoCommandResult.NOTHING_TO_UNDO;
        }
    }
}
