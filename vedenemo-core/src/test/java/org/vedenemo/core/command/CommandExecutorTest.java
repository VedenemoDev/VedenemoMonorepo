package org.vedenemo.core.command;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.Session;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.Optional;

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

        assertEquals(UndoResult.UNDONE, result);
        assertTrue(fixture.modelRoot.entities().isEmpty());
        assertTrue(fixture.session.commandHistory().isEmpty());
    }

    @Test
    void undoWithNoCommandReportsNothingToUndo() {
        Fixture fixture = fixtureWithSelectedModel();

        assertEquals(UndoResult.NOTHING_TO_UNDO, fixture.executor.undoLatest());
    }

    private static Fixture fixtureWithSelectedModel() {
        Fixture fixture = fixtureWithoutSelectedModel();
        fixture.session.selectModel("Example_Model");
        return fixture;
    }

    private static Fixture fixtureWithoutSelectedModel() {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.2.3");
        modelRegistry.add(modelRoot);
        Session session = Session.create();
        CommandExecutor executor = new CommandExecutor(new TestModelStorage(), modelRegistry, session);
        return new Fixture(modelRoot, session, executor);
    }

    private record Fixture(ModelRoot modelRoot, Session session, CommandExecutor executor) {
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
