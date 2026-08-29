package org.vedenemo.core.command;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ValueSetEntry;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandExecutorTest {

    @Test
    void createEntityCommandAddsEntityToSelectedModel() {
        Fixture fixture = fixtureWithSelectedModel();

        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));

        assertEquals(1, fixture.modelRoot.entities().size());
        assertEquals("Customer", fixture.modelRoot.entities().getFirst().azName());
        assertEquals("Customer", fixture.modelRoot.entities().getFirst().visName());
        assertEquals(fixture.modelRoot.version(), fixture.modelRoot.entities().getFirst().activeSince());
        assertTrue(fixture.modelRoot.entities().getFirst().attributes().isEmpty());
        assertEquals(1, fixture.session.commandHistory().size());
        assertEquals(1, fixture.commandJournal.listForModel("Example_Model").size());
    }

    @Test
    void duplicateEntityCommandFailsAndIsNotRecorded() {
        Fixture fixture = fixtureWithSelectedModel();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.executor.execute(new CreateEntityCommand("Example_Model", "customer", "Duplicate Customer"))
        );

        assertEquals(1, fixture.modelRoot.entities().size());
        assertEquals(1, fixture.session.commandHistory().size());
    }

    @Test
    void createEntityCommandRequiresSelectedModel() {
        Fixture fixture = fixtureWithoutSelectedModel();

        assertThrows(
                IllegalStateException.class,
                () -> fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"))
        );

        assertTrue(fixture.modelRoot.entities().isEmpty());
        assertTrue(fixture.session.commandHistory().isEmpty());
        assertTrue(fixture.commandJournal.listForModel("Example_Model").isEmpty());
    }

    @Test
    void createEntityCommandRequiresCommandTargetToMatchSelectedModel() {
        Fixture fixture = fixtureWithSelectedModel();

        assertThrows(
                IllegalStateException.class,
                () -> fixture.executor.execute(new CreateEntityCommand("Other_Model", "Customer", "Customer"))
        );

        assertTrue(fixture.modelRoot.entities().isEmpty());
        assertTrue(fixture.session.commandHistory().isEmpty());
    }

    @Test
    void undoAfterCreateRemovesEntityAndOriginalCommandFromHistory() {
        Fixture fixture = fixtureWithSelectedModel();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));

        UndoResult result = fixture.executor.undoLatest();

        assertEquals(UndoResult.Status.UNDONE, result.status());
        assertEquals("create-entity", result.undoneCommand());
        assertEquals("Example_Model", result.modelAzName());
        assertEquals("Customer", result.entityAzName());
        assertTrue(fixture.modelRoot.entities().isEmpty());
        assertTrue(fixture.session.commandHistory().isEmpty());
    }

    @Test
    void undoWithNoCommandReportsNothingToUndo() {
        Fixture fixture = fixtureWithSelectedModel();

        assertEquals(UndoResult.NOTHING_TO_UNDO, fixture.executor.undoLatest());
    }

    @Test
    void createAttributeCommandAddsAttributeToEntityInSelectedModel() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();

        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT
        ));

        VEntity entity = fixture.modelRoot.entities().getFirst();
        assertEquals(1, entity.attributes().size());
        assertEquals("Email", entity.attributes().getFirst().azName());
        assertEquals("Email", entity.attributes().getFirst().visName());
        assertEquals(DataType.TEXT, entity.attributes().getFirst().type());
        assertEquals(false, entity.attributes().getFirst().required());
        assertEquals(fixture.modelRoot.version(), entity.attributes().getFirst().activeSince());
        assertEquals(2, fixture.session.commandHistory().size());
    }

    @Test
    void createAttributeCommandCanAddRequiredAttribute() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();

        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT,
                true,
                null
        ));

        assertTrue(fixture.modelRoot.entities().getFirst().attributes().getFirst().required());
    }

    @Test
    void duplicateAttributeCommandFailsAndIsNotRecorded() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.executor.execute(new CreateAttributeCommand(
                        "Example_Model",
                        "Customer",
                        "email",
                        "Duplicate Email",
                        DataType.TEXT
                ))
        );

        assertEquals(1, fixture.modelRoot.entities().getFirst().attributes().size());
        assertEquals(2, fixture.session.commandHistory().size());
    }

    @Test
    void createAttributeCommandRequiresExistingEntity() {
        Fixture fixture = fixtureWithSelectedModel();

        assertThrows(
                IllegalStateException.class,
                () -> fixture.executor.execute(new CreateAttributeCommand(
                        "Example_Model",
                        "Missing",
                        "Email",
                        "Email",
                        DataType.TEXT
                ))
        );

        assertTrue(fixture.session.commandHistory().isEmpty());
    }

    @Test
    void undoAfterCreateAttributeRemovesAttributeAndOriginalCommandFromHistory() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.URL
        ));

        UndoResult result = fixture.executor.undoLatest();

        assertEquals(UndoResult.Status.UNDONE, result.status());
        assertEquals("create-attribute", result.undoneCommand());
        assertEquals("Example_Model", result.modelAzName());
        assertEquals("Customer", result.entityAzName());
        assertEquals("Email", result.attributeAzName());
        assertTrue(fixture.modelRoot.entities().getFirst().attributes().isEmpty());
        assertEquals(1, fixture.session.commandHistory().size());
        assertTrue(fixture.session.latestCommand().orElseThrow() instanceof CreateEntityCommand);
        assertEquals(1, fixture.commandJournal.listForModel("Example_Model").size());
        assertTrue(fixture.commandJournal.listForModel("Example_Model").getFirst() instanceof CreateEntityCommand);
    }

    @Test
    void createValueSetAndAttributeReferenceAreRecordedAndUndoable() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();

        fixture.executor.execute(new CreateValueSetCommand(
                "Example_Model",
                "TreeSpecies",
                DataType.TEXT,
                List.of(new ValueSetEntry("PINE", "Pine"), new ValueSetEntry("SPRUCE", "Spruce"))
        ));
        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Species",
                "Species",
                DataType.TEXT,
                "TreeSpecies"
        ));

        assertEquals(1, fixture.modelRoot.valueSets().size());
        assertEquals("TreeSpecies", fixture.modelRoot.entities().getFirst().attributes().getFirst().valueSetAzName());
        assertEquals(3, fixture.commandJournal.listForModel("Example_Model").size());

        UndoResult result = fixture.executor.undoLatest();

        assertEquals("create-attribute", result.undoneCommand());
        assertTrue(fixture.modelRoot.entities().getFirst().attributes().isEmpty());

        result = fixture.executor.undoLatest();

        assertEquals("create-value-set", result.undoneCommand());
        assertTrue(fixture.modelRoot.valueSets().isEmpty());
    }

    @Test
    void setAttributeValueSetAttachesCompatibleExistingAttributeAndUndoClearsIt() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateValueSetCommand(
                "Example_Model",
                "TreeSpecies",
                DataType.TEXT,
                List.of(new ValueSetEntry("PINE", "Pine"))
        ));
        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Species",
                "Species",
                DataType.TEXT
        ));

        fixture.executor.execute(new SetAttributeValueSetCommand("Example_Model", "Customer", "Species", "TreeSpecies"));

        assertEquals("TreeSpecies", fixture.modelRoot.entities().getFirst().attributes().getFirst().valueSetAzName());

        UndoResult result = fixture.executor.undoLatest();

        assertEquals("set-attribute-value-set", result.undoneCommand());
        assertEquals(null, fixture.modelRoot.entities().getFirst().attributes().getFirst().valueSetAzName());
    }

    @Test
    void attributeValueSetReferenceRequiresExistingCompatibleValueSet() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateValueSetCommand(
                "Example_Model",
                "Ratings",
                DataType.NUMERIC,
                List.of(new ValueSetEntry("1", "One"))
        ));

        assertThrows(IllegalArgumentException.class, () -> fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Species",
                "Species",
                DataType.TEXT,
                "Ratings"
        )));
        assertThrows(IllegalStateException.class, () -> fixture.executor.execute(new SetAttributeValueSetCommand(
                "Example_Model",
                "Customer",
                "Missing",
                "Ratings"
        )));
    }

    @Test
    void undoOperatesOnLatestCommandOnly() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateAttributeCommand(
                "Example_Model",
                "Customer",
                "Email",
                "Email",
                DataType.TEXT
        ));

        fixture.executor.undoLatest();

        assertEquals(1, fixture.session.commandHistory().size());
        assertEquals("Customer", fixture.modelRoot.entities().getFirst().azName());
        assertTrue(fixture.modelRoot.entities().getFirst().attributes().isEmpty());
    }

    @Test
    void createAssociationCommandAddsAssociationToSelectedModel() {
        Fixture fixture = fixtureWithSelectedModelAndTwoEntities();

        fixture.executor.execute(new CreateAssociationCommand(
                "Example_Model",
                AssociationKind.OWNERSHIP,
                "Customer_Orders",
                "orders",
                "Customer",
                "Order",
                Cardinality.parse("0..*")
        ));

        assertEquals(1, fixture.modelRoot.associations().size());
        assertEquals("Customer_Orders", fixture.modelRoot.associations().getFirst().azName());
        assertEquals("orders", fixture.modelRoot.associations().getFirst().visName());
        assertEquals("Customer", fixture.modelRoot.associations().getFirst().sourceEntityAzName());
        assertEquals("Order", fixture.modelRoot.associations().getFirst().targetEntityAzName());
        assertEquals(AssociationKind.OWNERSHIP, fixture.modelRoot.associations().getFirst().kind());
        assertEquals(Cardinality.parse("0..*"), fixture.modelRoot.associations().getFirst().cardinality());
        assertEquals(fixture.modelRoot.version(), fixture.modelRoot.associations().getFirst().activeSince());
        assertEquals(3, fixture.session.commandHistory().size());
        assertEquals(3, fixture.commandJournal.listForModel("Example_Model").size());
    }

    @Test
    void createRelationCommandAddsRelationWithNamedEndsToSelectedModel() {
        Fixture fixture = fixtureWithSelectedModelAndTwoEntities();

        fixture.executor.execute(new CreateAssociationCommand(
                "Example_Model",
                AssociationKind.RELATION,
                "Customer_Order",
                "orders",
                "Customer",
                "Order",
                null,
                "customer",
                "order",
                Cardinality.parse("1"),
                Cardinality.parse("0..*")
        ));

        assertEquals(1, fixture.modelRoot.associations().size());
        assertEquals(AssociationKind.RELATION, fixture.modelRoot.associations().getFirst().kind());
        assertEquals("customer", fixture.modelRoot.associations().getFirst().sourceRoleName());
        assertEquals("order", fixture.modelRoot.associations().getFirst().targetRoleName());
        assertEquals(Cardinality.parse("1"), fixture.modelRoot.associations().getFirst().sourceCardinality());
        assertEquals(Cardinality.parse("0..*"), fixture.modelRoot.associations().getFirst().targetCardinality());
        assertEquals(3, fixture.session.commandHistory().size());
    }

    @Test
    void createAssociationCommandRequiresExistingEndpoints() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.executor.execute(new CreateAssociationCommand(
                        "Example_Model",
                        AssociationKind.REFERENCE,
                        "Customer_Order",
                        "order",
                        "Customer",
                        "Order",
                        Cardinality.parse("1")
                ))
        );

        assertTrue(fixture.modelRoot.associations().isEmpty());
        assertEquals(1, fixture.session.commandHistory().size());
    }

    @Test
    void undoAfterCreateAssociationRemovesAssociationAndOriginalCommandFromHistory() {
        Fixture fixture = fixtureWithSelectedModelAndTwoEntities();
        fixture.executor.execute(new CreateAssociationCommand(
                "Example_Model",
                AssociationKind.REFERENCE,
                "Order_Customer",
                "customer",
                "Order",
                "Customer",
                Cardinality.parse("1")
        ));

        UndoResult result = fixture.executor.undoLatest();

        assertEquals(UndoResult.Status.UNDONE, result.status());
        assertEquals("create-association", result.undoneCommand());
        assertEquals("Example_Model", result.modelAzName());
        assertEquals("Order_Customer", result.associationAzName());
        assertTrue(fixture.modelRoot.associations().isEmpty());
        assertEquals(2, fixture.session.commandHistory().size());
        assertEquals(2, fixture.commandJournal.listForModel("Example_Model").size());
    }

    private static Fixture fixtureWithSelectedModel() {
        Fixture fixture = fixtureWithoutSelectedModel();
        fixture.session.selectModel("Example_Model");
        return fixture;
    }

    private static Fixture fixtureWithSelectedModelAndEntity() {
        Fixture fixture = fixtureWithSelectedModel();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Customer", "Customer"));
        return fixture;
    }

    private static Fixture fixtureWithSelectedModelAndTwoEntities() {
        Fixture fixture = fixtureWithSelectedModelAndEntity();
        fixture.executor.execute(new CreateEntityCommand("Example_Model", "Order", "Order"));
        return fixture;
    }

    private static Fixture fixtureWithoutSelectedModel() {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.2.3");
        modelRegistry.add(modelRoot);
        Session session = Session.create();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        CommandExecutor executor = new CommandExecutor(new TestModelStorage(), modelRegistry, commandJournal, session);
        return new Fixture(modelRoot, session, executor, commandJournal);
    }

    private record Fixture(ModelRoot modelRoot, Session session, CommandExecutor executor, ModelCommandJournal commandJournal) {
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
