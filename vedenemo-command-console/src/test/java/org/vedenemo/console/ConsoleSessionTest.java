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
    void rejectsModelSaveAndLoadAsPlainTextInWebConsole() {
        ConsoleSession session = new ConsoleSession(
                UUID.randomUUID(),
                new TestModelClient(),
                new TestSessionClient(),
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult saveResult = session.execute("msave");
        ConsoleCommandResult snapshotsResult = session.execute("snapshots");
        ConsoleCommandResult loadResult = session.execute("mload model.vdos");

        assertEquals(List.of("Command 'msave' is not supported in the web console because it requires local file access."), saveResult.outputLines());
        assertEquals(List.of("Command 'snapshots' is not supported in the web console because it requires local file access."), snapshotsResult.outputLines());
        assertEquals(List.of("Command 'mload' is not supported in the web console because it requires local file access."), loadResult.outputLines());
    }

    @Test
    void helpListsTerminalEquivalentNonFileCommands() {
        ConsoleSession session = new ConsoleSession(
                UUID.randomUUID(),
                new TestModelClient(),
                new TestSessionClient(),
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        ConsoleCommandResult result = session.execute("help");

        assertTrue(result.outputLines().contains("  add - add a new model or entity in the attached model"));
        assertTrue(result.outputLines().contains("  attr add - add an attribute to the selected entity"));
        assertTrue(result.outputLines().contains("  assoc add [ownership | reference | relation] - add an association or relation"));
        assertTrue(result.outputLines().contains("  msave [N | azName] [outputPath] - not supported in the web console"));
        assertTrue(result.outputLines().contains("  snapshots - not supported in the web console"));
        assertTrue(result.outputLines().contains("  mload <path | snapshot-number> - not supported in the web console"));
        assertTrue(result.outputLines().contains("  dumps - not supported in the web console"));
        assertTrue(result.outputLines().contains("  dsave [root-id | root-number | root-name] [outputPath] - not supported in the web console"));
        assertTrue(result.outputLines().contains("  dload <path | dump-number> - not supported in the web console"));
    }

    @Test
    void helpListsCloudSnapshotCommandsWhenEnabled() {
        ConsoleSession session = new ConsoleSession(
                UUID.randomUUID(),
                new TestModelClient(),
                new TestSessionClient(),
                new TestCommandClient(),
                ConsoleCapabilities.webConsoleWithCloudSnapshots()
        );

        ConsoleCommandResult result = session.execute("help");

        assertTrue(result.outputLines().contains("  msave [snapshotName] - save the attached model to a cloud snapshot"));
        assertTrue(result.outputLines().contains("  snapshots - list cloud snapshots"));
        assertTrue(result.outputLines().contains("  mload <snapshot-key | snapshot-number> - load a model from a cloud snapshot"));
        assertTrue(result.outputLines().contains("  dumps - list cloud model-instance data dumps"));
        assertTrue(result.outputLines().contains("  dsave [root-id | root-number | root-name] [dumpName] - save a model-instance root to a cloud dump"));
        assertTrue(result.outputLines().contains("  dload <dump-key | dump-number> - load a cloud dump into a new model-instance root"));
    }

    @Test
    void savesAttachedModelToCloudSnapshotWithPromptedName() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsoleWithCloudSnapshots()
        );

        session.execute("attach Example_Model");
        session.execute("msave");
        assertEquals("Snapshot name: ", session.prompt());
        ConsoleCommandResult result = session.execute("first");

        assertEquals(ConsoleCommandResult.Status.OK, result.status());
        assertEquals(List.of("Saved model Example_Model to cloud snapshot Example_Model/first.vdos."), result.outputLines());
        assertEquals("Example_Model", modelClient.savedSnapshotModelAzName);
        assertEquals("first", modelClient.savedSnapshotName);
    }

    @Test
    void listsAndLoadsCloudSnapshotByNumber() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.models.add(new ModelSummary("Loaded_Model", "Loaded Model", "1.0.0"));
        modelClient.snapshots.add(new SnapshotSummary("Loaded_Model/first.vdos", "Loaded_Model", "Loaded Model", "1.0.0", 2, "2026-07-28T18:30:00Z"));
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsoleWithCloudSnapshots()
        );

        ConsoleCommandResult snapshots = session.execute("snapshots");
        ConsoleCommandResult load = session.execute("mload 1");

        assertEquals("Cloud snapshots:", snapshots.outputLines().getFirst());
        assertTrue(snapshots.outputLines().contains("1. Loaded_Model/first.vdos - Loaded Model (Loaded_Model) version 1.0.0, 2 commands, saved 2026-07-28T18:30:00Z"));
        assertEquals(List.of("Loaded model Loaded_Model from cloud snapshot Loaded_Model/first.vdos."), load.outputLines());
        assertEquals("Loaded_Model/first.vdos", modelClient.loadedSnapshotKey);
        assertEquals("Loaded_Model", sessionClient.selectedModelAzName);
    }

    @Test
    void promptsForReplacementAzNameWhenCloudSnapshotLoadConflicts() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Existing_Model", "Existing Model", "1.0.0"));
        modelClient.models.add(new ModelSummary("Replacement_Model", "Replacement Model", "1.0.0"));
        modelClient.duplicateOnFirstLoad = true;
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsoleWithCloudSnapshots()
        );

        ConsoleCommandResult firstLoad = session.execute("mload Existing_Model/saved.vdos");
        assertEquals(List.of("model load failed: model already exists: Existing_Model"), firstLoad.outputLines());
        assertEquals("New model azName for import, or blank to cancel: ", session.prompt());

        ConsoleCommandResult renamedLoad = session.execute("Replacement_Model");

        assertEquals(List.of("Loaded model Replacement_Model from cloud snapshot Existing_Model/saved.vdos."), renamedLoad.outputLines());
        assertEquals("Replacement_Model", modelClient.loadedModelAzNameOverride);
        assertEquals("Replacement_Model", sessionClient.selectedModelAzName);
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

    @Test
    void listsModelAssociationsWhenNoEntityIsSelected() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.associations.add(new AssociationSummary(
                "Customer_Orders",
                "orders",
                "OWNERSHIP",
                "Customer",
                "Order",
                "0..*",
                null,
                null,
                null,
                "0..*",
                "1.0.0",
                null
        ));
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        ConsoleCommandResult result = session.execute("associations");

        assertEquals(ConsoleCommandResult.Status.OK, result.status());
        assertEquals("Associations for model Example_Model:", result.outputLines().getFirst());
        assertTrue(result.outputLines().contains("1. orders (Customer_Orders) OWNERSHIP Customer -> Order [0..*] active since 1.0.0"));
    }

    @Test
    void addCreatesModelThroughPromptFlow() {
        TestModelClient modelClient = new TestModelClient();
        TestSessionClient sessionClient = new TestSessionClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                new TestCommandClient(),
                ConsoleCapabilities.webConsole()
        );

        session.execute("add");
        assertEquals("Model visible name: ", session.prompt());
        session.execute("Example Model");
        assertEquals("Model azName [Example_Model]: ", session.prompt());
        ConsoleCommandResult result = session.execute("");

        assertEquals(ConsoleCommandResult.Status.OK, result.status());
        assertTrue(result.outputLines().contains("Added and attached model Example_Model."));
        assertEquals("Example_Model", sessionClient.selectedModelAzName);
        assertEquals("VedenemoCli[Example_Model]>", session.prompt());
    }

    @Test
    void addCreatesEntityThroughPromptFlowWhenModelIsAttached() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("add");
        assertEquals("Entity visible name: ", session.prompt());
        session.execute("Customer");
        assertEquals("Entity azName [Customer]: ", session.prompt());
        ConsoleCommandResult result = session.execute("");

        assertEquals(List.of("Entity Customer added."), result.outputLines());
        assertEquals("Customer", commandClient.createdEntityAzName);
        assertEquals("VedenemoCli[Example_Model]>", session.prompt());
    }

    @Test
    void attrAddCreatesAttributeThroughPromptFlow() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("entity Customer");
        session.execute("attr add");
        assertEquals("Attribute visible name: ", session.prompt());
        session.execute("Email Address");
        assertEquals("Attribute azName [Email_Address]: ", session.prompt());
        session.execute("");
        assertEquals("Attribute data type [TEXT]: ", session.prompt());
        ConsoleCommandResult result = session.execute("url");

        assertEquals(List.of("Attribute Email_Address added."), result.outputLines());
        assertEquals("Customer", commandClient.createdAttributeEntityAzName);
        assertEquals("Email_Address", commandClient.createdAttributeAzName);
        assertEquals("URL", commandClient.createdAttributeDataType);
        assertEquals("VedenemoCli[Example_Model/Customer]>", session.prompt());
    }

    @Test
    void attrAddNormalizesIsoDateAndTimeDataTypes() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("entity Customer");
        session.execute("attr add");
        session.execute("Updated At");
        session.execute("");
        session.execute("datetime");

        assertEquals("DATETIME", commandClient.createdAttributeDataType);
    }

    @Test
    void assocAddCreatesOwnershipThroughPromptFlow() {
        TestModelClient modelClient = modelWithAssociationEntities();
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("entities");
        session.execute("assoc add ownership");
        session.execute("1");
        session.execute("2");
        session.execute("orders");
        session.execute("0..*");
        ConsoleCommandResult result = session.execute("");

        assertEquals(List.of("Association Customer_orders added."), result.outputLines());
        assertEquals("ownership", commandClient.createdAssociationKind);
        assertEquals("Customer_orders", commandClient.createdAssociationAzName);
        assertEquals("Customer", commandClient.createdAssociationSourceEntityAzName);
        assertEquals("Order", commandClient.createdAssociationTargetEntityAzName);
        assertEquals("0..*", commandClient.createdAssociationCardinality);
    }

    @Test
    void assocAddCreatesRelationThroughNumberedKindPromptFlow() {
        TestModelClient modelClient = modelWithAssociationEntities();
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("entities");
        session.execute("assoc add");
        assertEquals("Association kind [1 ownership, 2 reference, 3 relation]: ", session.prompt());
        session.execute("3");
        session.execute("1");
        session.execute("customer");
        session.execute("0..*");
        session.execute("2");
        session.execute("order");
        session.execute("1..*");
        session.execute("orders");
        ConsoleCommandResult result = session.execute("");

        assertEquals(List.of("Relation Customer_orders added."), result.outputLines());
        assertEquals("relation", commandClient.createdAssociationKind);
        assertEquals("customer", commandClient.createdAssociationSourceRoleName);
        assertEquals("order", commandClient.createdAssociationTargetRoleName);
        assertEquals("0..*", commandClient.createdAssociationSourceCardinality);
        assertEquals("1..*", commandClient.createdAssociationTargetCardinality);
    }

    @Test
    void escapeCancelsPromptFlowWithoutExecutingCommand() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        TestSessionClient sessionClient = new TestSessionClient();
        TestCommandClient commandClient = new TestCommandClient();
        ConsoleSession session = new ConsoleSession(
                sessionClient.sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.webConsole()
        );

        session.execute("attach Example_Model");
        session.execute("add");
        ConsoleCommandResult result = session.execute("\u001b");

        assertEquals(List.of("Operation cancelled."), result.outputLines());
        assertEquals(null, commandClient.createdEntityAzName);
        assertEquals("VedenemoCli[Example_Model]>", session.prompt());
    }

    private static TestModelClient modelWithAssociationEntities() {
        TestModelClient modelClient = new TestModelClient();
        modelClient.models.add(new ModelSummary("Example_Model", "Example Model", "1.0.0"));
        modelClient.entities.add(new EntitySummary("Customer", "Customer", "1.0.0", null));
        modelClient.entities.add(new EntitySummary("Order", "Order", "1.0.0", null));
        return modelClient;
    }

    private static final class TestModelClient implements ModelClient {
        private final List<ModelSummary> models = new ArrayList<>();
        private final List<EntitySummary> entities = new ArrayList<>();
        private final List<AssociationSummary> associations = new ArrayList<>();
        private final List<SnapshotSummary> snapshots = new ArrayList<>();
        private boolean pingCalled;
        private String savedSnapshotModelAzName;
        private String savedSnapshotName;
        private String loadedSnapshotKey;
        private String loadedModelAzNameOverride;
        private boolean duplicateOnFirstLoad;

        @Override
        public void ping() {
            pingCalled = true;
        }

        @Override
        public List<ModelSummary> listModels() {
            return List.copyOf(models);
        }

        @Override
        public ModelSummary addModel(String azName, String visName, String version) {
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
            return List.of();
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
        public String exportScript(String modelAzName) throws IOException {
            throw new IOException("not implemented");
        }

        @Override
        public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException {
            throw new IOException("not implemented");
        }

        @Override
        public ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException {
            loadedSnapshotKey = snapshotKey;
            loadedModelAzNameOverride = modelAzNameOverride;
            if (duplicateOnFirstLoad && modelAzNameOverride == null) {
                duplicateOnFirstLoad = false;
                throw new ModelAlreadyExistsException("model load failed: model already exists: Existing_Model");
            }
            String modelAzName = modelAzNameOverride == null ? "Loaded_Model" : modelAzNameOverride;
            return new ModelImportResult(modelAzName, 2);
        }

        @Override
        public List<SnapshotSummary> listSnapshots() {
            return List.copyOf(snapshots);
        }

        @Override
        public SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) {
            savedSnapshotModelAzName = modelAzName;
            savedSnapshotName = snapshotName;
            SnapshotSummary snapshot = new SnapshotSummary(modelAzName + "/" + snapshotName + ".vdos", modelAzName, modelAzName, "1.0.0", 1, "2026-07-28T18:30:00Z");
            snapshots.add(snapshot);
            return snapshot;
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
        private String createdEntityAzName;
        private String createdAttributeEntityAzName;
        private String createdAttributeAzName;
        private String createdAttributeDataType;
        private String createdAssociationKind;
        private String createdAssociationAzName;
        private String createdAssociationSourceEntityAzName;
        private String createdAssociationTargetEntityAzName;
        private String createdAssociationCardinality;
        private String createdAssociationSourceRoleName;
        private String createdAssociationTargetRoleName;
        private String createdAssociationSourceCardinality;
        private String createdAssociationTargetCardinality;

        @Override
        public void createEntity(UUID sessionId, String entityAzName, String entityVisName) {
            createdEntityAzName = entityAzName;
        }

        @Override
        public void createAttribute(
                UUID sessionId,
                String entityAzName,
                String attributeAzName,
                String attributeVisName,
                String dataType
        ) {
            createdAttributeEntityAzName = entityAzName;
            createdAttributeAzName = attributeAzName;
            createdAttributeDataType = dataType;
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
            createdAssociationSourceEntityAzName = sourceEntityAzName;
            createdAssociationTargetEntityAzName = targetEntityAzName;
            createdAssociationCardinality = cardinality;
            createdAssociationSourceRoleName = sourceRoleName;
            createdAssociationTargetRoleName = targetRoleName;
            createdAssociationSourceCardinality = sourceCardinality;
            createdAssociationTargetCardinality = targetCardinality;
        }

        @Override
        public UndoCommandResult undo(UUID sessionId) {
            return UndoCommandResult.NOTHING_TO_UNDO;
        }
    }
}
