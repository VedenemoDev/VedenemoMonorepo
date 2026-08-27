package org.vedenemo.cli;

import org.vedenemo.console.AssociationSummary;
import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.CommandClient;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelInstanceRootSummary;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SessionClient;
import org.vedenemo.console.SnapshotSummary;
import org.vedenemo.console.UndoCommandResult;
import org.vedenemo.console.ValueSetEntryInput;
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
        assertTrue(result.output.contains("assoc add [ownership | reference | relation] - add an association or relation"));
        assertTrue(result.output.contains("undo - undo the latest backend command"));
        assertTrue(result.output.contains("msave [N | azName] [outputPath] - save a model to a .vdos file"));
        assertTrue(result.output.contains("snapshots - list .vdos files from the .vedenemo directory"));
        assertTrue(result.output.contains("mload <path | snapshot-number> - load a model from a .vdos file"));
        assertTrue(result.output.contains("roots - list model-instance roots for the attached model"));
        assertTrue(result.output.contains("dumps - list .vdmp files from the .vedenemo directory"));
        assertTrue(result.output.contains("dsave [root-id | root-number | root-name] [outputPath] - save a model-instance root to a .vdmp file"));
        assertTrue(result.output.contains("dload <path | dump-number> - load a .vdmp file into a new model-instance root"));
        assertTrue(result.output.contains("Esc - cancel the current interactive prompt"));
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
    void escapeDuringInteractivePromptCancelsOperation() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));

        Result result = run(
                sessionClient,
                modelClient,
                commandClient,
                "attach Example_Model\nadd\n\u001b\nexit\n"
        );

        assertEquals("Example_Model", sessionClient.selectedModelAzName);
        assertEquals(null, commandClient.createdEntityAzName);
        assertTrue(result.output.contains("Operation cancelled."));
        assertTrue(result.output.contains("VedenemoCli[Example_Model]>"));
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
    void commandWordsAreCaseInsensitiveButAzNameParametersAreCaseSensitive() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "LiSt\nAtTaCh Example_Model\nDeTaCh\nattach example_model\nEXIT\n");

        assertEquals(null, sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("1. Example Model (Example_Model) version 1.0.0"));
        assertTrue(result.output.contains("Attached to model Example_Model."));
        assertTrue(result.output.contains("Detached from model."));
        assertTrue(result.output.contains("No model found with azName example_model."));
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
    void rootsRequiresAttachedModel() {
        Result result = run(new TestSessionClient(UUID.randomUUID()), new TestModelClient(), new TestCommandClient(), "roots\nexit\n");

        assertTrue(result.output.contains("Attach a model before listing model-instance roots."));
    }

    @Test
    void rootsPrintsEmptyMessageForAttachedModel() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nroots\nexit\n");

        assertTrue(result.output.contains("No model-instance roots available for model Example_Model."));
    }

    @Test
    void rootsPrintsNumberedModelInstanceRoots() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.instanceRoots.add(new ModelInstanceRootSummary("root-1", "Example_Model", "1.0.0", "First root"));
        modelClient.instanceRoots.add(new ModelInstanceRootSummary("root-2", "Example_Model", "1.1.0", "Second root"));

        Result result = run(sessionClient, modelClient, new TestCommandClient(), "attach Example_Model\nroots\nexit\n");

        assertTrue(result.output.contains("Model-instance roots for model Example_Model:"));
        assertTrue(result.output.contains("1. First root version 1.0.0 (root-1)"));
        assertTrue(result.output.contains("2. Second root version 1.1.0 (root-2)"));
    }

    @Test
    void dsaveByNumberUsesLatestRootsListing() throws IOException {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.instanceRoots.add(new ModelInstanceRootSummary("root-1", "Example_Model", "1.0.0", "First root"));
        modelClient.instanceRoots.add(new ModelInstanceRootSummary("root-2", "Example_Model", "1.0.0", "Second root"));

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "attach Example_Model\nroots\ndsave 2 selected\nexit\n",
                tempDirectory
        );

        assertEquals("root-2", modelClient.exportedDumpRootId);
        assertTrue(Files.readString(tempDirectory.resolve("selected.vdmp")).contains("\"format\":\"vedenemo-instance-dump\""));
        assertTrue(result.output.contains("Saved model-instance root root-2 to " + tempDirectory.resolve("selected.vdmp") + "."));
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
    void attrAddNormalizesIsoDateAndTimeDataTypes() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nUpdated At\n\ndatetime\nexit\n");

        assertEquals("DATETIME", commandClient.createdAttributeDataType);
    }

    @Test
    void attrAddNormalizesLocationLineAndAreaDataTypes() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));

        run(sessionClient, modelClient, commandClient, "attach Example_Model\nentity Customer\nattr add\nPath\n\nlocation_area\nexit\n");

        assertEquals("LOCATION_AREA", commandClient.createdAttributeDataType);
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
                "Email",
                null
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
    void assocAddCreatesAssociationWithPromptedValues() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        modelClient.entities.add(new EntitySummary("Order", "Order", "1.0.0", null));

        Result result = run(
                sessionClient,
                modelClient,
                commandClient,
                "attach Example_Model\nentities\nassoc add ownership\n1\n2\norders\n0..*\n\nexit\n"
        );

        assertEquals("ownership", commandClient.createdAssociationKind);
        assertEquals("Customer_orders", commandClient.createdAssociationAzName);
        assertEquals("orders", commandClient.createdAssociationVisName);
        assertEquals("Customer", commandClient.createdAssociationSourceEntityAzName);
        assertEquals("Order", commandClient.createdAssociationTargetEntityAzName);
        assertEquals("0..*", commandClient.createdAssociationCardinality);
        assertTrue(result.output.contains("Association Customer_orders added."));
    }

    @Test
    void assocAddRelationCreatesRelationWithPromptedEnds() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Student", "Student", "1.0.0", null));
        modelClient.entities.add(new EntitySummary("Course", "Course", "1.0.0", null));

        Result result = run(
                sessionClient,
                modelClient,
                commandClient,
                "attach Example_Model\nentities\nassoc add relation\n1\nstudent\n0..*\n2\ncourse\n1..*\nenrollment\n\nexit\n"
        );

        assertEquals("relation", commandClient.createdAssociationKind);
        assertEquals("Student_enrollment", commandClient.createdAssociationAzName);
        assertEquals("enrollment", commandClient.createdAssociationVisName);
        assertEquals("Student", commandClient.createdAssociationSourceEntityAzName);
        assertEquals("Course", commandClient.createdAssociationTargetEntityAzName);
        assertEquals("1..*", commandClient.createdAssociationCardinality);
        assertEquals("student", commandClient.createdAssociationSourceRoleName);
        assertEquals("course", commandClient.createdAssociationTargetRoleName);
        assertEquals("0..*", commandClient.createdAssociationSourceCardinality);
        assertEquals("1..*", commandClient.createdAssociationTargetCardinality);
        assertTrue(result.output.contains("Relation Student_enrollment added."));
    }

    @Test
    void assocAddPromptsForNumberedRelationKind() {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        TestCommandClient commandClient = new TestCommandClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Student", "Student", "1.0.0", null));
        modelClient.entities.add(new EntitySummary("Course", "Course", "1.0.0", null));

        Result result = run(
                sessionClient,
                modelClient,
                commandClient,
                "attach Example_Model\nentities\nassoc add\n3\n1\nstudent\n0..*\n2\ncourse\n1..*\nenrollment\n\nexit\n"
        );

        assertEquals("relation", commandClient.createdAssociationKind);
        assertEquals("Student_enrollment", commandClient.createdAssociationAzName);
        assertTrue(result.output.contains("Association kind [1 ownership, 2 reference, 3 relation]: "));
        assertTrue(result.output.contains("Relation Student_enrollment added."));
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
                "attach Example_Model\nmsave\n\nexit\n",
                tempDirectory
        );

        assertEquals("Example_Model", modelClient.exportedModelAzName);
        assertEquals("vedenemo-script 1\n", Files.readString(tempDirectory.resolve("Example_Model.vdos"), StandardCharsets.UTF_8));
        assertTrue(result.output.contains("Output file [Example_Model.vdos]: "));
        assertTrue(result.output.contains("Saved model Example_Model to "));
    }

    @Test
    void saveWithoutArgumentDefaultsToVedenemoDirectoryWhenItExists() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "script";
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "attach Example_Model\nmsave\n\nexit\n",
                tempDirectory
        );

        assertEquals("script", Files.readString(snapshotDirectory.resolve("Example_Model.vdos"), StandardCharsets.UTF_8));
        assertTrue(!Files.exists(tempDirectory.resolve("Example_Model.vdos")));
        assertTrue(result.output.contains("Output file [.vedenemo/Example_Model.vdos]: "));
    }

    @Test
    void saveByListNumberUsesInlinePathAndAddsExtension() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "script";

        run(sessionClient, modelClient, new TestCommandClient(), "list\nmsave 1 export-file\nexit\n", tempDirectory);

        assertEquals("Example_Model", modelClient.exportedModelAzName);
        assertEquals("script", Files.readString(tempDirectory.resolve("export-file.vdos"), StandardCharsets.UTF_8));
    }

    @Test
    void saveRelativeInlinePathUsesVedenemoDirectoryWhenItExists() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "script";
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));

        run(sessionClient, modelClient, new TestCommandClient(), "list\nmsave 1 export-file\nexit\n", tempDirectory);

        assertEquals("script", Files.readString(snapshotDirectory.resolve("export-file.vdos"), StandardCharsets.UTF_8));
        assertTrue(!Files.exists(tempDirectory.resolve("export-file.vdos")));
    }

    @Test
    void saveAbsoluteInlinePathBypassesVedenemoDirectory() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.exportedScript = "script";
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));
        Path target = tempDirectory.resolve("absolute-target");

        run(sessionClient, modelClient, new TestCommandClient(), "list\nmsave 1 " + target + "\nexit\n", tempDirectory);

        assertEquals("script", Files.readString(tempDirectory.resolve("absolute-target.vdos"), StandardCharsets.UTF_8));
        assertTrue(!Files.exists(snapshotDirectory.resolve("absolute-target.vdos")));
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
                "attach Example_Model\nmsave\n\ny\nexit\n",
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
                "msave\nexit\n",
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
                "mload example\nexit\n",
                tempDirectory
        );

        assertEquals("vedenemo-script 1\n", modelClient.importedScript);
        assertEquals("Imported_Model", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Loaded model Imported_Model from "));
        assertTrue(result.output.contains("with 2 commands."));
    }

    @Test
    void snapshotsListsVdosFilesFromVedenemoDirectory() throws Exception {
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));
        Files.writeString(snapshotDirectory.resolve("zeta.vdos"), "zeta", StandardCharsets.UTF_8);
        Files.writeString(snapshotDirectory.resolve("Alpha.vdos"), "alpha", StandardCharsets.UTF_8);
        Files.writeString(snapshotDirectory.resolve("notes.txt"), "notes", StandardCharsets.UTF_8);

        Result result = run(
                new TestSessionClient(UUID.randomUUID()),
                new TestModelClient(),
                new TestCommandClient(),
                "snapshots\nexit\n",
                tempDirectory
        );

        assertTrue(result.output.contains("1. Alpha.vdos"));
        assertTrue(result.output.contains("2. zeta.vdos"));
        assertTrue(!result.output.contains("notes.txt"));
    }

    @Test
    void snapshotsReportsMissingDirectory() {
        Result result = run(
                new TestSessionClient(UUID.randomUUID()),
                new TestModelClient(),
                new TestCommandClient(),
                "snapshots\nexit\n",
                tempDirectory
        );

        assertTrue(result.output.contains("No .vedenemo directory found at " + tempDirectory.resolve(".vedenemo") + "."));
    }

    @Test
    void loadBySnapshotNumberUsesLatestSnapshotList() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));
        Files.writeString(snapshotDirectory.resolve("Beta.vdos"), "beta-script", StandardCharsets.UTF_8);
        Files.writeString(snapshotDirectory.resolve("Alpha.vdos"), "alpha-script", StandardCharsets.UTF_8);

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "snapshots\nmload 2\nexit\n",
                tempDirectory
        );

        assertEquals("beta-script", modelClient.importedScript);
        assertEquals("Imported_Model", sessionClient.selectedModelAzName);
        assertTrue(result.output.contains("Loaded model Imported_Model from " + snapshotDirectory.resolve("Beta.vdos")));
    }

    @Test
    void loadNumericFileNameWorksWithoutSnapshotList() throws Exception {
        TestModelClient modelClient = new TestModelClient();
        Files.writeString(tempDirectory.resolve("1.vdos"), "number-script", StandardCharsets.UTF_8);

        run(
                new TestSessionClient(UUID.randomUUID()),
                modelClient,
                new TestCommandClient(),
                "mload 1\nexit\n",
                tempDirectory
        );

        assertEquals("number-script", modelClient.importedScript);
    }

    @Test
    void loadBareNamePrefersVedenemoDirectoryWhenSnapshotExists() throws Exception {
        TestSessionClient sessionClient = new TestSessionClient(UUID.randomUUID());
        TestModelClient modelClient = new TestModelClient();
        Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve(".vedenemo"));
        Files.writeString(tempDirectory.resolve("Levykokoelma.vdos"), "root-script", StandardCharsets.UTF_8);
        Files.writeString(snapshotDirectory.resolve("Levykokoelma.vdos"), "snapshot-script", StandardCharsets.UTF_8);

        Result result = run(
                sessionClient,
                modelClient,
                new TestCommandClient(),
                "mload Levykokoelma\nexit\n",
                tempDirectory
        );

        assertEquals("snapshot-script", modelClient.importedScript);
        assertTrue(result.output.contains("Loaded model Imported_Model from " + snapshotDirectory.resolve("Levykokoelma.vdos")));
    }

    @Test
    void loadBareNameFallsBackToWorkingDirectory() throws Exception {
        TestModelClient modelClient = new TestModelClient();
        Files.writeString(tempDirectory.resolve("example.vdos"), "root-script", StandardCharsets.UTF_8);

        run(
                new TestSessionClient(UUID.randomUUID()),
                modelClient,
                new TestCommandClient(),
                "mload example\nexit\n",
                tempDirectory
        );

        assertEquals("root-script", modelClient.importedScript);
    }

    @Test
    void loadMissingFilePrintsMessage() {
        Result result = run(
                new TestSessionClient(UUID.randomUUID()),
                new TestModelClient(),
                new TestCommandClient(),
                "mload missing\nexit\n",
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
                "mload example\nRenamed_Model\nexit\n",
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
        private final List<AssociationSummary> associations = new ArrayList<>();
        private final List<ModelInstanceRootSummary> instanceRoots = new ArrayList<>();
        private boolean pingCalled;
        private IOException addFailure;
        private String exportedScript = "";
        private String exportedModelAzName;
        private String exportedDumpRootId;
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
        public List<AssociationSummary> listAssociations(String modelAzName) {
            return List.copyOf(associations);
        }

        @Override
        public List<AssociationSummary> listAssociations(String modelAzName, String entityAzName) {
            return associations.stream()
                    .filter(association -> association.sourceEntityAzName().equals(entityAzName)
                            || association.targetEntityAzName().equals(entityAzName))
                    .toList();
        }

        @Override
        public List<ModelInstanceRootSummary> listInstanceRoots(String modelAzName) {
            return instanceRoots.stream()
                    .filter(root -> root.modelAzName().equals(modelAzName))
                    .toList();
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

        @Override
        public List<SnapshotSummary> listSnapshots() throws IOException {
            throw new IOException("cloud snapshots are not supported by terminal tests");
        }

        @Override
        public SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) throws IOException {
            throw new IOException("cloud snapshots are not supported by terminal tests");
        }

        @Override
        public ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException {
            throw new IOException("cloud snapshots are not supported by terminal tests");
        }

        @Override
        public String exportDump(String modelAzName, String instanceRootId) {
            exportedDumpRootId = instanceRootId;
            return "{\"format\":\"vedenemo-instance-dump\"}";
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
        private String createdAssociationKind;
        private String createdAssociationAzName;
        private String createdAssociationVisName;
        private String createdAssociationSourceEntityAzName;
        private String createdAssociationTargetEntityAzName;
        private String createdAssociationCardinality;
        private String createdAssociationSourceRoleName;
        private String createdAssociationTargetRoleName;
        private String createdAssociationSourceCardinality;
        private String createdAssociationTargetCardinality;
        private boolean undoCalled;
        private UndoCommandResult undoResult = UndoCommandResult.undone(
                "create-entity",
                "Example_Model",
                "Customer",
                null,
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
                String dataType,
                String valueSetAzName
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
        public void createValueSet(UUID sessionId, String valueSetAzName, String dataType, List<ValueSetEntryInput> entries) {
        }

        @Override
        public void setAttributeValueSet(UUID sessionId, String entityAzName, String attributeAzName, String valueSetAzName) {
        }

        @Override
        public void createAssociation(
                UUID sessionId,
                String kind,
                String associationAzName,
                String associationVisName,
                String sourceEntityAzName,
                String targetEntityAzName,
                String cardinality,
                String sourceRoleName,
                String targetRoleName,
                String sourceCardinality,
                String targetCardinality
        ) {
            createdAssociationKind = kind;
            createdAssociationAzName = associationAzName;
            createdAssociationVisName = associationVisName;
            createdAssociationSourceEntityAzName = sourceEntityAzName;
            createdAssociationTargetEntityAzName = targetEntityAzName;
            createdAssociationCardinality = cardinality;
            createdAssociationSourceRoleName = sourceRoleName;
            createdAssociationTargetRoleName = targetRoleName;
            createdAssociationSourceCardinality = sourceCardinality;
            createdAssociationTargetCardinality = targetCardinality;
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
