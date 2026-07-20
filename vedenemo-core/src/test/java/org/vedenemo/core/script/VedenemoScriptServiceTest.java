package org.vedenemo.core.script;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VedenemoScriptServiceTest {

    @Test
    void exportIncludesModelCommandsAndSnapshotLifecycleFields() {
        Fixture fixture = fixture();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));
        fixture.executor.execute(new org.vedenemo.core.command.CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT
        ));

        String script = fixture.scriptService.exportModel("example_model");

        assertTrue(script.contains("vedenemo-script 1"));
        assertTrue(script.contains("model azName=Example_Model visName=\"Example Model\" version=1.0.0"));
        assertTrue(script.contains("create-entity model=Example_Model entity=Customer visName=\"Customer\" activeSince=1.0.0"));
        assertTrue(script.contains("create-attribute model=Example_Model entity=Customer attribute=Email visName=\"Email\" dataType=TEXT activeSince=1.0.0"));
        assertTrue(script.contains("entity azName=Customer visName=\"Customer\" activeSince=1.0.0 deprecatedSince=null"));
        assertTrue(script.contains("attribute entity=Customer azName=Email visName=\"Email\" dataType=TEXT activeSince=1.0.0 deprecatedSince=null"));
    }

    @Test
    void importReplaysCommandsAsBaselineAndStoresJournal() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Email visName="Email" dataType=TEXT activeSince=1.0.0 deprecatedSince=null
                """;

        VedenemoScriptImportResult result = fixture.scriptService.importModel(script, null);

        ModelRoot model = fixture.modelRegistry.find("Example_Model").orElseThrow();
        assertEquals("Example_Model", result.modelAzName());
        assertEquals(2, result.commandCount());
        assertEquals(1, model.entities().size());
        assertEquals(1, model.entities().getFirst().attributes().size());
        assertEquals(2, fixture.commandJournal.listForModel("Example_Model").size());
    }

    @Test
    void importCanRenameModelAndRetargetCommands() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                """;

        VedenemoScriptImportResult result = fixture.scriptService.importModel(script, "Renamed_Model");

        assertEquals("Renamed_Model", result.modelAzName());
        assertTrue(fixture.modelRegistry.find("Example_Model").isEmpty());
        assertTrue(fixture.modelRegistry.find("Renamed_Model").isPresent());
        assertEquals(1, fixture.commandJournal.listForModel("Renamed_Model").size());
    }

    @Test
    void duplicateImportIsRejected() {
        Fixture fixture = fixture();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.scriptService.importModel(fixture.scriptService.exportModel("Example_Model"), null)
        );

        assertTrue(exception.getMessage().contains("model already exists"));
    }

    @Test
    void snapshotMismatchIsRejected() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Changed Customer" activeSince=1.0.0 deprecatedSince=null
                """;

        assertThrows(IllegalArgumentException.class, () -> fixture.scriptService.importModel(script, null));
        assertTrue(fixture.modelRegistry.find("Example_Model").isEmpty());
    }

    private static Fixture fixture() {
        Fixture fixture = emptyFixture();
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        fixture.modelRegistry.add(modelRoot);
        Session session = Session.create();
        session.selectModel("Example_Model");
        return new Fixture(
                fixture.modelRegistry,
                fixture.commandJournal,
                new CommandExecutor(new TestModelStorage(), fixture.modelRegistry, fixture.commandJournal, session),
                fixture.scriptService
        );
    }

    private static Fixture emptyFixture() {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        return new Fixture(
                modelRegistry,
                commandJournal,
                null,
                new VedenemoScriptService(modelRegistry, commandJournal)
        );
    }

    private record Fixture(
            ModelRegistry modelRegistry,
            ModelCommandJournal commandJournal,
            CommandExecutor executor,
            VedenemoScriptService scriptService
    ) {
    }

    private static final class TestModelStorage implements ModelStorage {

        @Override
        public void save(String modelId, ModelRoot model) {
        }

        @Override
        public Optional<ModelRoot> load(String modelId) {
            return Optional.empty();
        }
    }
}
