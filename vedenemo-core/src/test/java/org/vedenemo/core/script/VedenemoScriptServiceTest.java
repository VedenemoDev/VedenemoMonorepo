package org.vedenemo.core.script;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.CreateAssociationCommand;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.CreateValueSetCommand;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.command.SetAttributeValueSetCommand;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ValueSetEntry;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VedenemoScriptServiceTest {

    @Test
    void exportIncludesModelCommandsAndSnapshotLifecycleFields() {
        Fixture fixture = fixture();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Order", "Order"));
        fixture.executor.execute(new CreateValueSetCommand(
                "Example_Model",
                "TreeSpecies",
                DataType.TEXT,
                java.util.List.of(new ValueSetEntry("PINE", "Pine"), new ValueSetEntry("SPRUCE", "Spruce"))
        ));
        fixture.executor.execute(new org.vedenemo.core.command.CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT
        ));
        fixture.executor.execute(new SetAttributeValueSetCommand("Example_Model", "Customer", "Email", "TreeSpecies"));
        fixture.executor.execute(new org.vedenemo.core.command.CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Location",
                "Location",
                DataType.LOCATION
        ));
        fixture.executor.execute(new org.vedenemo.core.command.CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Path",
                "Path",
                DataType.LOCATION_LINE
        ));
        fixture.executor.execute(new org.vedenemo.core.command.CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Boundary",
                "Boundary",
                DataType.LOCATION_AREA
        ));
        fixture.executor.execute(new CreateAssociationCommand(
                "Example_Model",
                AssociationKind.OWNERSHIP,
                "Customer_Orders",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*")
        ));
        fixture.executor.execute(new CreateAssociationCommand(
                "Example_Model",
                AssociationKind.RELATION,
                "Customer_Order_Relation",
                "orders",
                "Customer",
                "Order",
                null,
                "customer",
                "order",
                Cardinality.parse("1"),
                Cardinality.parse("0..*")
        ));

        String script = fixture.scriptService.exportModel("example_model");

        assertTrue(script.contains("vedenemo-script 1"));
        assertTrue(script.contains("model azName=Example_Model visName=\"Example Model\" version=1.0.0"));
        assertTrue(script.contains("create-entity model=Example_Model entity=Customer visName=\"Customer\" activeSince=1.0.0"));
        assertTrue(script.contains("create-value-set model=Example_Model valueSet=TreeSpecies dataType=TEXT entry1=\"PINE\" entry1VisName=\"Pine\" entry2=\"SPRUCE\" entry2VisName=\"Spruce\" activeSince=1.0.0"));
        assertTrue(script.contains("create-attribute model=Example_Model entity=Customer attribute=Email visName=\"Email\" dataType=TEXT required=false activeSince=1.0.0"));
        assertTrue(script.contains("set-attribute-value-set model=Example_Model entity=Customer attribute=Email valueSet=TreeSpecies activeSince=1.0.0"));
        assertTrue(script.contains("create-attribute model=Example_Model entity=Customer attribute=Location visName=\"Location\" dataType=LOCATION required=false activeSince=1.0.0"));
        assertTrue(script.contains("create-attribute model=Example_Model entity=Customer attribute=Path visName=\"Path\" dataType=LOCATION_LINE required=false activeSince=1.0.0"));
        assertTrue(script.contains("create-attribute model=Example_Model entity=Customer attribute=Boundary visName=\"Boundary\" dataType=LOCATION_AREA required=false activeSince=1.0.0"));
        assertTrue(script.contains("create-association model=Example_Model kind=OWNERSHIP association=Customer_Orders visName=\"orders\" source=Customer target=Order cardinality=0..* activeSince=1.0.0"));
        assertTrue(script.contains("create-association model=Example_Model kind=RELATION association=Customer_Order_Relation visName=\"orders\" source=Customer sourceRole=\"customer\" sourceCardinality=1 target=Order targetRole=\"order\" targetCardinality=0..* cardinality=0..* activeSince=1.0.0"));
        assertTrue(script.contains("entity azName=Customer visName=\"Customer\" activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("value-set azName=TreeSpecies dataType=TEXT entry1=\"PINE\" entry1VisName=\"Pine\" entry2=\"SPRUCE\" entry2VisName=\"Spruce\""));
        assertTrue(script.contains("attribute entity=Customer azName=Email visName=\"Email\" dataType=TEXT required=false valueSet=TreeSpecies activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("attribute entity=Customer azName=Location visName=\"Location\" dataType=LOCATION required=false activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("attribute entity=Customer azName=Path visName=\"Path\" dataType=LOCATION_LINE required=false activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("attribute entity=Customer azName=Boundary visName=\"Boundary\" dataType=LOCATION_AREA required=false activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("association azName=Customer_Orders visName=\"orders\" kind=OWNERSHIP source=Customer target=Order cardinality=0..* activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
        assertTrue(script.contains("association azName=Customer_Order_Relation visName=\"orders\" kind=RELATION source=Customer sourceRole=\"customer\" sourceCardinality=1 target=Order targetRole=\"order\" targetCardinality=0..* cardinality=0..* activeSince=1.0.0 deprecatedSince=null retiredSince=null"));
    }

    @Test
    void importReplaysCommandsAsBaselineAndStoresJournal() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-entity model=Example_Model entity=Order visName="Order" activeSince=1.0.0
                create-value-set model=Example_Model valueSet=TreeSpecies dataType=TEXT entry1="PINE" entry1VisName="Pine" entry2="SPRUCE" entry2VisName="Spruce" activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT activeSince=1.0.0
                set-attribute-value-set model=Example_Model entity=Customer attribute=Email valueSet=TreeSpecies activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Location visName="Location" dataType=LOCATION activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Path visName="Path" dataType=LOCATION_LINE activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Boundary visName="Boundary" dataType=LOCATION_AREA activeSince=1.0.0
                create-association model=Example_Model kind=REFERENCE association=Order_Customer visName="customer" source=Order target=Customer cardinality=1 activeSince=1.0.0
                create-association model=Example_Model kind=RELATION association=Customer_Order_Relation visName="orders" source=Customer sourceRole="customer" sourceCardinality=1 target=Order targetRole="order" targetCardinality=0..* cardinality=0..* activeSince=1.0.0

                snapshot
                value-set azName=TreeSpecies dataType=TEXT entry1="PINE" entry1VisName="Pine" entry2="SPRUCE" entry2VisName="Spruce"
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                entity azName=Order visName="Order" activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Email visName="Email" dataType=TEXT valueSet=TreeSpecies activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Location visName="Location" dataType=LOCATION activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Path visName="Path" dataType=LOCATION_LINE activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Boundary visName="Boundary" dataType=LOCATION_AREA activeSince=1.0.0 deprecatedSince=null
                association azName=Order_Customer visName="customer" kind=REFERENCE source=Order target=Customer cardinality=1 activeSince=1.0.0 deprecatedSince=null
                association azName=Customer_Order_Relation visName="orders" kind=RELATION source=Customer sourceRole="customer" sourceCardinality=1 target=Order targetRole="order" targetCardinality=0..* cardinality=0..* activeSince=1.0.0 deprecatedSince=null
                """;

        VedenemoScriptImportResult result = fixture.scriptService.importModel(script, null);

        ModelRoot model = fixture.modelRegistry.find("Example_Model").orElseThrow();
        assertEquals("Example_Model", result.modelAzName());
        assertEquals(10, result.commandCount());
        assertEquals(2, model.entities().size());
        assertEquals(4, model.entities().getFirst().attributes().size());
        assertEquals(false, model.entities().getFirst().attributes().getFirst().required());
        assertEquals("TreeSpecies", model.entities().getFirst().attributes().getFirst().valueSetAzName());
        assertEquals(1, model.valueSets().size());
        assertEquals(2, model.associations().size());
        assertEquals(AssociationKind.RELATION, model.associations().get(1).kind());
        assertEquals("customer", model.associations().get(1).sourceRoleName());
        assertEquals("order", model.associations().get(1).targetRoleName());
        assertEquals(10, fixture.commandJournal.listForModel("Example_Model").size());
    }

    @Test
    void importPreservesRequiredAttributes() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Email visName="Email" dataType=TEXT required=true activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Email visName="Email" dataType=TEXT required=true activeSince=1.0.0 deprecatedSince=null
                """;

        fixture.scriptService.importModel(script, null);

        assertTrue(fixture.modelRegistry.find("Example_Model").orElseThrow()
                .entities().getFirst().attributes().getFirst().required());
    }

    @Test
    void importMetsapalstaScript() throws Exception {
        Fixture fixture = emptyFixture();

        fixture.scriptService.importModel(Files.readString(Path.of("../.vedenemo/Metsapalsta.vdos")), null);

        ModelRoot model = fixture.modelRegistry.find("Metsapalsta").orElseThrow();
        assertEquals("Metsäpalsta", model.visName());
        assertEquals(4, model.entities().size());
        assertEquals(1, model.valueSets().size());
        assertEquals(3, model.associations().size());
        assertTrue(findEntity(model, "Metsapalsta").attributes().stream()
                .filter(attribute -> attribute.azName().equals("nimi"))
                .findFirst()
                .orElseThrow()
                .required());
        assertEquals("PuulajiNimi", findEntity(model, "Puulaji").attributes().getFirst().valueSetAzName());
        assertEquals(AssociationKind.OWNERSHIP, model.associations().getFirst().kind());
        assertEquals(AssociationKind.REFERENCE, model.associations().get(1).kind());
        assertEquals(AssociationKind.REFERENCE, model.associations().get(2).kind());
    }

    @Test
    void importRejectsUndefinedValueSetReference() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-attribute model=Example_Model entity=Customer attribute=Species visName="Species" dataType=TEXT valueSet=MissingSet activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                attribute entity=Customer azName=Species visName="Species" dataType=TEXT valueSet=MissingSet activeSince=1.0.0 deprecatedSince=null
                """;

        assertThrows(IllegalArgumentException.class, () -> fixture.scriptService.importModel(script, null));
        assertTrue(fixture.modelRegistry.find("Example_Model").isEmpty());
    }

    @Test
    void importStillAcceptsScriptsWithoutAssociations() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                """;

        fixture.scriptService.importModel(script, null);

        ModelRoot model = fixture.modelRegistry.find("Example_Model").orElseThrow();
        assertEquals(1, model.entities().size());
        assertTrue(model.associations().isEmpty());
    }

    @Test
    void associationSnapshotMismatchIsRejected() {
        Fixture fixture = emptyFixture();
        String script = """
                vedenemo-script 1

                model azName=Example_Model visName="Example Model" version=1.0.0

                commands
                create-entity model=Example_Model entity=Customer visName="Customer" activeSince=1.0.0
                create-entity model=Example_Model entity=Order visName="Order" activeSince=1.0.0
                create-association model=Example_Model kind=REFERENCE association=Order_Customer visName="customer" source=Order target=Customer cardinality=1 activeSince=1.0.0

                snapshot
                entity azName=Customer visName="Customer" activeSince=1.0.0 deprecatedSince=null
                entity azName=Order visName="Order" activeSince=1.0.0 deprecatedSince=null
                association azName=Order_Customer visName="changed" kind=REFERENCE source=Order target=Customer cardinality=1 activeSince=1.0.0 deprecatedSince=null
                """;

        assertThrows(IllegalArgumentException.class, () -> fixture.scriptService.importModel(script, null));
        assertTrue(fixture.modelRegistry.find("Example_Model").isEmpty());
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

    private static org.vedenemo.core.model.VEntity findEntity(ModelRoot model, String entityAzName) {
        return model.entities().stream()
                .filter(entity -> entity.azName().equals(entityAzName))
                .findFirst()
                .orElseThrow();
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
