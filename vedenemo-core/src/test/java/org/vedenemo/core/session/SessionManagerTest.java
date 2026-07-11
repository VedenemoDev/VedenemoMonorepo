package org.vedenemo.core.session;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.command.CommandExecutor;
import org.vedenemo.core.command.NoOpCommand;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionManagerTest {

    @Test
    void startsSessionWithBoundCommandExecutor() {
        SessionManager manager = new SessionManager(new TestModelStorage());

        Session session = manager.startSession();
        CommandExecutor executor = manager.findExecutor(session.id()).orElseThrow();

        assertSame(session, executor.session());
        assertEquals(1, manager.activeSessionCount());
    }

    @Test
    void commandExecutorRecordsCommandsInBoundSession() {
        SessionManager manager = new SessionManager(new TestModelStorage());
        Session session = manager.startSession();
        CommandExecutor executor = manager.findExecutor(session.id()).orElseThrow();
        NoOpCommand command = new NoOpCommand();

        executor.execute(command);

        assertEquals(1, session.commandHistory().size());
        assertSame(command, session.commandHistory().getFirst());
    }

    @Test
    void endsSessionAndRemovesExecutor() {
        SessionManager manager = new SessionManager(new TestModelStorage());
        Session session = manager.startSession();

        assertEquals(Optional.of(session), manager.endSession(session.id()));

        assertTrue(manager.findSession(session.id()).isEmpty());
        assertTrue(manager.findExecutor(session.id()).isEmpty());
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void unknownSessionEndReturnsEmpty() {
        SessionManager manager = new SessionManager(new TestModelStorage());

        assertTrue(manager.endSession(UUID.randomUUID()).isEmpty());
    }

    private static final class TestModelStorage implements ModelStorage {
        private final Map<String, ModelRoot> models = Map.of();

        @Override
        public void save(String modelId, ModelRoot modelRoot) {
            models.size();
        }

        @Override
        public Optional<ModelRoot> load(String modelId) {
            return Optional.empty();
        }
    }
}
