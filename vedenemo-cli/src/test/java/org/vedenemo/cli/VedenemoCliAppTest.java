package org.vedenemo.cli;

import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.CommandClient;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SessionClient;
import org.vedenemo.console.UndoCommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VedenemoCliAppTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void startsSessionShowsPromptAndCleansUpOnExit() {
        UUID sessionId = UUID.randomUUID();
        TestSessionClient sessionClient = new TestSessionClient(sessionId);
        TestModelClient modelClient = new TestModelClient();

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "exit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.output.contains("Session with UUID " + sessionId + " is created / attached to."));
        assertTrue(result.output.contains("VedenemoCli>"));
        assertEquals(sessionId, sessionClient.endedSessionId);
    }

    @Test
    void emptyLineEchoesEmptyLineAndContinuesPrompting() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());

        Result result = run(sessionClient, new TestModelClient(), new TestCommandClient(), "\nexit\n");

        assertEquals(0, result.exitCode);
        assertTrue(result.output.contains("VedenemoCli>\nVedenemoCli>"));
        assertEquals(sessionClient.sessionId, sessionClient.endedSessionId);
    }

    @Test
    void helpPrintsAvailableCommands() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), new TestCommandClient(), "help\nexit\n");

        assertTrue(result.output.contains("ping - check backend connectivity"));
        assertTrue(result.output.contains("list - list existing models"));
        assertTrue(result.output.contains("add - add a new model"));
        assertTrue(result.output.contains("attach [N | azName] - attach to a listed model"));
        assertTrue(result.output.contains("detach - detach from the current model"));
        assertTrue(result.output.contains("entities - list entities in the attached model"));
        assertTrue(result.output.contains("entity [N | azName] - select an entity in the attached model"));
        assertTrue(result.output.contains("entity detach - clear the selected entity"));
        assertTrue(result.output.contains("attributes - list attributes in the selected entity"));
        assertTrue(result.output.contains("attr add - add an attribute to the selected entity"));
        assertTrue(result.output.contains("undo - undo the latest backend command"));
        assertTrue(result.output.contains("save [N | azName] [outputPath] - save a model to a .vdos file"));
        assertTrue(result.output.contains("load <path> - load a model from a .vdos file"));
        assertTrue(result.output.contains("exit - end the session and exit"));
    }

    @Test
    void pingPrintsBackendOkMessage() {
        TestModelClient modelClient = new TestModelClient();

        Result result = run(new TestSessionClient(UUID.randomUUID()), modelClient, new TestCommandClient(), "ping\nexit\n");

        assertTrue(modelClient.pingCalled);
        assertTrue(result.output.contains("Backend responded OK."));
    }

    @Test
    void listPrintsEmptyMessageWhenNoModelsExist() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), new TestCommandClient(), "list\nexit\n");

        assertTrue(result.output.contains("No models available."));
    }

    @Test
    void listPrintsNumberedModels() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));
        modelClient.models.add(new ModelSummary("Second_Model", "Second Model", "2.0.0"));

        Result result = run(new TestSessionClient(UUID.randomUUID()), modelClient, new TestCommandClient(), "list\nexit\n");

        assertTrue(result.output.contains("1. First Model (First) version 1.0.0"));
        assertTrue(result.output.contains("2. Second Model (Second_Model) version 2.0.0"));
    }

    @Test
    void attachByNumberUsesLatestListAndUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "list\nattach 1\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Attached to model First."));
        assertTrue(result.output.contains("VedenemoCli[First]>"));
    }

    @Test
    void attachByAzNameUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach First\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("VedenemoCli[First]>"));
    }

    @Test
    void attachWithoutArgumentAsksForIdentifier() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach\nFirst\nexit\n");

        assertEquals("First", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Model number or azName: "));
    }

    @Test
    void attachByNumberWithoutListDoesNotFetchAutomatically() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach 1\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Run list first before attaching by number."));
    }

    @Test
    void invalidAttachKeepsPreviousPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "list\nattach 9\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("No model found for list number 9."));
        assertTrue(result.output.contains("VedenemoCli>"));
    }

    @Test
    void detachClearsAttachedModelAndPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("First", "First Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach First\ndetach\nexit\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(sessionClient.clearSelectedModelCalled);
        assertTrue(result.output.contains("Detached from model."));
        assertTrue(result.output.contains("VedenemoCli[First]>"));
        assertTrue(result.output.endsWith("VedenemoCli>"));
    }

    @Test
    void detachClearsAttachedEntityToo() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentity Customer\ndetach\nexit\n");

        assertTrue(result.output.contains("VedenemoCli[Example_Model/Customer]>"));
        assertTrue(result.output.endsWith("VedenemoCli>"));
    }

    @Test
    void detachWithoutAttachedModelPrintsMessage() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), new TestCommandClient(), "detach\nexit\n");

        assertTrue(result.output.contains("No model is currently attached."));
    }

    @Test
    void addPromptsCreatesVersionOneAndAttachesModel() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "add\nExample Model\n\nexit\n");

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

        Result result = run(new TestSessionClient(UUID.randomUUID()), modelClient, new TestCommandClient(), "add\nExample Model\nExample\nexit\n");

        assertTrue(result.output.contains("model add failed with HTTP status 409: duplicate"));
        assertTrue(result.output.contains("VedenemoCli>"));
    }

    @Test
    void addWithAttachedModelCreatesEntityCommand() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nadd\nCustomer Entity\n\nexit\n");

        assertEquals("Customer_Entity", commandClient.createdEntityAzName);
        assertEquals("Customer Entity", commandClient.createdEntityVisName);
        assertEquals(sessionClient.sessionId, commandClient.createEntitySessionId);
        assertTrue(result.output.contains("Entity visible name: "));
        assertTrue(result.output.contains("Entity azName [Customer_Entity]: "));
        assertTrue(result.output.contains("Entity Customer_Entity added."));
    }

    @Test
    void addSuggestsAzNameWithDigitsAfterFirstLetter() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "add\nModel 2026 Draft\n\nexit\n");

        assertEquals("Model_2026_Draft", modelClient.models.getFirst().azName());
        assertTrue(result.output.contains("Model azName [Model_2026_Draft]: "));
    }

    @Test
    void addWithAttachedModelReportsCommandFailureWithoutExiting() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        commandClient.createFailure = new IOException("entity add failed with HTTP status 400: duplicate");

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nadd\nCustomer\nCustomer\nexit\n");

        assertTrue(result.output.contains("entity add failed with HTTP status 400: duplicate"));
        assertTrue(result.output.contains("VedenemoCli[Example_Model]>"));
    }

    @Test
    void entitiesRequiresAttachedModel() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), new TestCommandClient(), "entities\nexit\n");

        assertTrue(result.output.contains("Attach a model before listing entities."));
    }

    @Test
    void entitiesPrintsNumberedRows() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentities\nexit\n");

        assertTrue(result.output.contains("1. Customer (Customer) active since 1.0.0"));
    }

    @Test
    void entityByNumberUsesLatestEntityListAndUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentities\nentity 1\nexit\n");

        assertTrue(result.output.contains("Selected entity Customer."));
        assertTrue(result.output.contains("VedenemoCli[Example_Model/Customer]>"));
    }

    @Test
    void entityByAzNameUpdatesPrompt() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentity Customer\nexit\n");

        assertTrue(result.output.contains("VedenemoCli[Example_Model/Customer]>"));
    }

    @Test
    void entityDetachClearsEntityButKeepsModel() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentity Customer\nentity detach\nexit\n");

        assertTrue(result.output.contains("Entity detached."));
        assertTrue(result.output.endsWith("VedenemoCli[Example_Model]>"));
    }

    @Test
    void attributesRequiresSelectedEntity() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nattributes\nexit\n");

        assertTrue(result.output.contains("Select an entity before listing attributes."));
    }

    @Test
    void attributesPrintsDataTypeAndLifecycleFields() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        modelClient.attributes.add(new AttributeSummary("Email", "Email", "TEXT", "1.0.0", null));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nentity Customer\nattributes\nexit\n");

        assertTrue(result.output.contains("1. Email (Email) type TEXT active since 1.0.0"));
    }

    @Test
    void attrAddPromptsAndSendsCreateAttribute() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nEmail Address\n\nurl\nexit\n");

        assertEquals(sessionClient.sessionId, commandClient.createAttributeSessionId);
        assertEquals("Customer", commandClient.createdAttributeEntityAzName);
        assertEquals("Email_Address", commandClient.createdAttributeAzName);
        assertEquals("Email Address", commandClient.createdAttributeVisName);
        assertEquals("URL", commandClient.createdAttributeDataType);
        assertTrue(result.output.contains("Attribute visible name: "));
        assertTrue(result.output.contains("Attribute azName [Email_Address]: "));
        assertTrue(result.output.contains("Attribute data type [TEXT]: "));
        assertTrue(result.output.contains("Attribute Email_Address added."));
    }

    @Test
    void attrAddSuggestsAzNameWithNumericSuffix() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nAttribute 2\n\n\nexit\n");

        assertEquals("Attribute_2", commandClient.createdAttributeAzName);
        assertTrue(result.output.contains("Attribute azName [Attribute_2]: "));
    }

    @Test
    void attrAddSuggestsAzNameWithNumericWordSuffix() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nAddress Line 1\n\n\nexit\n");

        assertEquals("Address_Line_1", commandClient.createdAttributeAzName);
        assertTrue(result.output.contains("Attribute azName [Address_Line_1]: "));
    }

    @Test
    void attrAddSkipsLeadingDigitsInAzNameSuggestion() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\n2 Attribute\n\n\nexit\n");

        assertEquals("Attribute", commandClient.createdAttributeAzName);
        assertTrue(result.output.contains("Attribute azName [Attribute]: "));
    }

    @Test
    void attrAddDefaultsBlankDataTypeToText() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nEmail\n\n\nexit\n");

        assertEquals("TEXT", commandClient.createdAttributeDataType);
    }

    @Test
    void attrAddReportsDuplicateFailureAndKeepsContext() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        commandClient.createAttributeFailure = new IOException("attribute add failed with HTTP status 400: duplicate");

        Result result = run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nEmail\nEmail\ntext\nexit\n");

        assertTrue(result.output.contains("Attribute was not added: attribute add failed with HTTP status 400: duplicate."));
        assertTrue(result.output.contains("VedenemoCli[Example_Model/Customer]>"));
    }

    @Test
    void undoPrintsSuccessWhenBackendUndoSucceeds() {
        TestCommandClient commandClient = new TestCommandClient();

        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), commandClient, "undo\nexit\n");

        assertTrue(commandClient.undoCalled);
        assertTrue(result.output.contains("Undo completed: removed entity Customer from model Example_Model."));
    }

    @Test
    void undoPrintsAttributeSpecificMessageWhenBackendUndoSucceeds() {
        TestCommandClient commandClient = new TestCommandClient();
        commandClient.undoResult = UndoCommandResult.undone(
                "create-attribute",
                "Example_Model",
                "Customer",
                "Email"
        );

        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), commandClient, "undo\nexit\n");

        assertTrue(result.output.contains("Undo completed: removed attribute Email from entity Customer in model Example_Model."));
    }

    @Test
    void undoPrintsClearMessageWhenNothingCanBeUndone() {
        TestCommandClient commandClient = new TestCommandClient();
        commandClient.undoResult = UndoCommandResult.NOTHING_TO_UNDO;

        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), commandClient, "undo\nexit\n");

        assertTrue(result.output.contains("Nothing to undo."));
    }

    @Test
    void undoPrintsUnexpectedResponseErrors() {
        TestCommandClient commandClient = new TestCommandClient();
        commandClient.undoFailure = new IOException("Unexpected response code: 500");

        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), commandClient, "undo\nexit\n");

        assertTrue(result.output.contains("Unexpected response code: 500"));
    }

    @Test
    void saveWithoutArgumentUsesAttachedModelAndDefaultPromptPath() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "vedenemo-script 1\n";

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "attach Example_Model\nsave\n\nexit\n",
                tempDirectory
        );

        assertEquals("Example_Model", modelClient.exportedModelAzName);
        assertEquals("vedenemo-script 1\n", Files.readString(tempDirectory.resolve("Example_Model.vdos"), StandardCharsets.UTF_8));
        assertTrue(result.output.contains("Output file [Example_Model.vdos]: "));
        assertTrue(result.output.contains("Saved model Example_Model to "));
    }

    @Test
    void saveByListNumberUsesInlinePathAndAddsExtension() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "script";

        run(sessionClient, modelClient, new TestCommandClient(), "list\nsave 1 export-file\nexit\n", tempDirectory);

        assertEquals("Example_Model", modelClient.exportedModelAzName);
        assertEquals("script", Files.readString(tempDirectory.resolve("export-file.vdos"), StandardCharsets.UTF_8));
    }

    @Test
    void savePromptsBeforeOverwritingExistingFile() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "new";
        Files.writeString(tempDirectory.resolve("Example_Model.vdos"), "old", StandardCharsets.UTF_8);

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "attach Example_Model\nsave\n\ny\nexit\n",
                tempDirectory
        );

        assertEquals("new", Files.readString(tempDirectory.resolve("Example_Model.vdos"), StandardCharsets.UTF_8));
        assertTrue(result.output.contains("Overwrite? [y/N]: "));
    }

    @Test
    void saveWithoutAttachedModelOrArgumentPrintsMessage() {
        Result result = run(
                new TestSessionClient(UUID.randomUUID()),
                new TestModelClient(),
                new TestCommandClient(),
                "save\nexit\n",
                tempDirectory
        );

        assertTrue(result.output.contains("Attach a model or provide a model number or azName before saving."));
    }

    @Test
    void loadReadsFileAutoAddsExtensionImportsAndAttachesModel() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        Files.writeString(tempDirectory.resolve("example.vdos"), "vedenemo-script 1\n", StandardCharsets.UTF_8);

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "load example\nexit\n",
                tempDirectory
        );

        assertEquals("vedenemo-script 1\n", modelClient.importedScript);
        assertEquals("Imported_Model", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Loaded model Imported_Model from "));
        assertTrue(result.output.contains("with 2 commands."));
    }

    @Test
    void loadMissingFilePrintsMessage() {
        Result result = run(
                new TestSessionClient(UUID.randomUUID()),
                new TestModelClient(),
                new TestCommandClient(),
                "load missing\nexit\n",
                tempDirectory
        );

        assertTrue(result.output.contains("File not found: "));
    }

    @Test
    void loadDuplicatePromptsForRenameAndRetries() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.importFailuresBeforeSuccess = 1;
        Files.writeString(tempDirectory.resolve("example.vdos"), "script", StandardCharsets.UTF_8);

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "load example\nRenamed_Model\nexit\n",
                tempDirectory
        );

        assertEquals("Renamed_Model", modelClient.importedOverride);
        assertEquals("Renamed_Model", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("New model azName for import, or blank to cancel: "));
    }

    private static Result run(TestSessionClient sessionClient, TestModelClient modelClient, CommandClient commandClient, String input) {
        return run(sessionClient, modelClient, commandClient, input, Path.of("").toAbsolutePath());
    }

    private static Result run(
            TestSessionClient sessionClient,
            TestModelClient modelClient,
            CommandClient commandClient,
            String input,
            Path workingDirectory
    ) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VedenemoCliApp app = new VedenemoCliApp(
                sessionClient,
                modelClient,
                commandClient,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false,
                workingDirectory
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
        private final List<EntitySummary> entities = new ArrayList<>();
        private final List<AttributeSummary> attributes = new ArrayList<>();
        private boolean pingCalled;
        private IOException addFailure;
        private String exportedScript = "";
        private String exportedModelAzName;
        private String importedScript;
        private String importedOverride;
        private int importFailuresBeforeSuccess;

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
            if (addFailure != null) {
                throw addFailure;
            }
            ModelSummary model = new ModelSummary(azName, visName, version);
            models.add(model);
            return model;
        }

        @Override
        public List<EntitySummary> listEntities(String modelAzName) {
            return List.copyOf(entities);
        }

        @Override
        public List<AttributeSummary> listAttributes(String modelAzName, String entityAzName) {
            return List.copyOf(attributes);
        }

        @Override
        public String exportScript(String modelAzName) {
            exportedModelAzName = modelAzName;
            return exportedScript;
        }

        @Override
        public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException {
            importedScript = script;
            importedOverride = modelAzNameOverride;
            if (importFailuresBeforeSuccess > 0) {
                importFailuresBeforeSuccess--;
                throw new ModelAlreadyExistsException("model load failed with HTTP status 409: duplicate");
            }
            String modelAzName = modelAzNameOverride == null ? "Imported_Model" : modelAzNameOverride;
            models.add(new ModelSummary(modelAzName, modelAzName, "1.0.0"));
            return new ModelImportResult(modelAzName, 2);
        }
    }

    private static final class TestCommandClient implements CommandClient {
        private UUID createEntitySessionId;
        private String createdEntityAzName;
        private String createdEntityVisName;
        private UUID createAttributeSessionId;
        private String createdAttributeEntityAzName;
        private String createdAttributeAzName;
        private String createdAttributeVisName;
        private String createdAttributeDataType;
        private boolean undoCalled;
        private UndoCommandResult undoResult = UndoCommandResult.undone(
                "create-entity",
                "Example_Model",
                "Customer",
                null
        );
        private IOException createFailure;
        private IOException createAttributeFailure;
        private IOException undoFailure;

        @Override
        public void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException {
            if (createFailure != null) {
                throw createFailure;
            }
            createEntitySessionId = sessionId;
            createdEntityAzName = entityAzName;
            createdEntityVisName = entityVisName;
        }

        @Override
        public void createAttribute(
                UUID sessionId,
                String entityAzName,
                String attributeAzName,
                String attributeVisName,
                String dataType
        ) throws IOException {
            if (createAttributeFailure != null) {
                throw createAttributeFailure;
            }
            createAttributeSessionId = sessionId;
            createdAttributeEntityAzName = entityAzName;
            createdAttributeAzName = attributeAzName;
            createdAttributeVisName = attributeVisName;
            createdAttributeDataType = dataType;
        }

        @Override
        public UndoCommandResult undo(UUID sessionId) throws IOException {
            if (undoFailure != null) {
                throw undoFailure;
            }
            undoCalled = true;
            return undoResult;
        }
    }
}
