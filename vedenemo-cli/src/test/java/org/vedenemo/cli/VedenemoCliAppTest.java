package org.vedenemo.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VedenemoCliAppTest {

    @Test
    void startsSessionShowsPromptAndCleansUpOnExit() {
        UUID sessionId = UUID.randomUUID();
        TestSessionClient sessionClient = new TestSessionClient(sessionId);
        TestModelClient modelClient = new TestModelClient();

        Result result = run(sessionClient, modelClient, "exit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.output.contains("Session with UUID " + sessionId + " is created / attached to."));
        assertTrue(result.output.contains("VedenemoCli>"));
        assertEquals(sessionId, sessionClient.endedSessionId);
    }

    @Test
    void emptyLineEchoesEmptyLineAndContinuesPrompting() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());

        Result result = run(sessionClient, new TestModelClient(), "\nexit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.output.contains("VedenemoCli>\nVedenemoCli>"));
        assertEquals(sessionClient.sessionId, sessionClient.endedSessionId);
    }

    @Test
    void helpPrintsAvailableCommands() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), "help\nexit\n");

        assertTrue(result.output.contains("list - list existing models"));
        assertTrue(result.output.contains("add - add a new model"));
        assertTrue(result.output.contains("attach [N | azName] - attach to a listed model"));
        assertTrue(result.output.contains("detach - detach from the current model"));
        assertTrue(result.output.contains("exit - end the session and exit"));
    }

    @Test
    void listPrintsEmptyMessageWhenNoModelsExist() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), "list\nexit\n");

        assertTrue(result.output.contains("No models available."));
    }

    @Test
    void listPrintsNumberedModels() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));
        modelClient.models.add(new ModelSummary("Second_Model", "Second Model", "2.0.0"));

        Result result = run(new TestSessionClient(UUID.randomUUID()), modelClient, "list\nexit\n");

        assertTrue(result.output.contains("1. First Model (First) version 1.0.0"));
        assertTrue(result.output.contains("2. Second Model (Second_Model) version 2.0.0"));
    }

    @Test
    void attachByNumberUsesLatestListAndUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "list\nattach 1\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Attached to model First."));
        assertTrue(result.output.contains("VedenemoCli[First]>"));
    }

    @Test
    void attachByAzNameUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "attach First\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("VedenemoCli[First]>"));
    }

    @Test
    void attachWithoutArgumentAsksForIdentifier() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "attach\nFirst\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Model number or azName: "));
    }

    @Test
    void attachByNumberWithoutListDoesNotFetchAutomatically() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "attach 1\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Run list first before attaching by number."));
    }

    @Test
    void invalidAttachKeepsPreviousPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "list\nattach 9\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("No model found for list number 9."));
        assertTrue(result.output.contains("VedenemoCli>"));
    }

    @Test
    void detachClearsAttachedModelAndPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, "attach First\ndetach\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(sessionClient.clearSelectedModelCalled);
        assertTrue(result.output.contains("Detached from model."));
        assertTrue(result.output.contains("VedenemoCli[First]>"));
        assertTrue(result.output.endsWith("VedenemoCli>"));
    }

    @Test
    void detachWithoutAttachedModelPrintsMessage() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), "detach\nexit\n");

        assertTrue(result.output.contains("No model is currently attached."));
    }

    @Test
    void addPromptsCreatesVersionOneAndAttachesModel() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();

        Result result = run(sessionClient, modelClient, "add\nExample Model\n\nexit\n");

        assertEquals(List.of(new ModelSummary("Example_Model", "Example Model", "1.0.0")), modelClient.models);
        assertEquals("Example_Model", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Model visible name: "));
        assertTrue(result.output.contains("Model azName [Example_Model]: "));
        assertTrue(result.output.contains("Added and attached model Example_Model."));
        assertTrue(result.output.contains("VedenemoCli[Example_Model]>"));
    }

    @Test
    void addReportsBackendValidationFailureWithoutExiting() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.addFailure = new IOException("model add failed with HTTP status 409: duplicate");

        Result result = run(new TestSessionClient(UUID.randomUUID()), modelClient, "add\nExample Model\nExample\nexit\n");

        assertTrue(result.output.contains("model add failed with HTTP status 409: duplicate"));
        assertTrue(result.output.contains("VedenemoCli>"));
    }

    private static Result run(TestSessionClient sessionClient, TestModelClient modelClient, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VedenemoCliApp app = new VedenemoCliApp(
                sessionClient,
                modelClient,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false
        );
        return new Result(app.run(), output.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output) {
    }

    private static final class TestSessionClient implements SessionClient {
        private final UUID sessionId;
        private UUID endedSessionId;
        private String selectedModelAzName;
        private boolean clearSelectedModelCalled;

        private TestSessionClient(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public UUID startSession() {
            return sessionId;
        }

        @Override
        public void endSession(UUID sessionId) throws IOException {
            endedSessionId = sessionId;
        }

        @Override
        public void selectModel(UUID sessionId, String azName) {
            selectedModelAzName = azName;
            clearSelectedModelCalled = false;
        }

        @Override
        public void clearSelectedModel(UUID sessionId) {
            selectedModelAzName = null;
            clearSelectedModelCalled = true;
        }
    }

    private static final class TestModelClient implements ModelClient {
        private final List<ModelSummary> models = new ArrayList<>();
        private IOException addFailure;

        @Override
        public List<ModelSummary> listModels() {
            return List.copyOf(models);
        }

        @Override
        public ModelSummary addModel(String azName, String visName, String version) throws IOException {
            if (addFailure != null) {
                throw addFailure;
            }
            ModelSummary model = new ModelSummary(azName, visName, version);
            models.add(model);
            return model;
        }
    }
}
