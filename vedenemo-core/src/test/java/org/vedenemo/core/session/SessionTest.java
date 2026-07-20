package org.vedenemo.core.session;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.command.Command;
import org.vedenemo.core.command.NoOpCommand;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionTest {

    @Test
    void createdSessionHasUuidAndNoSelectedModel() {
        Session session = Session.create();

        assertTrue(session.id() instanceof UUID);
        assertTrue(session.selectedModelAzName().isEmpty());
    }

    @Test
    void recordsCommandHistoryInExecutionAndReverseOrder() {
        Session session = Session.create();
        Command first = new NoOpCommand();
        Command second = new NoOpCommand();

        session.record(first);
        session.record(second);

        assertEquals(List.of(first, second), session.commandHistory());
        assertEquals(List.of(second, first), session.commandHistoryForUndo());
    }

    @Test
    void commandHistorySnapshotsAreReadOnly() {
        Session session = Session.create();
        session.record(new NoOpCommand());

        assertThrows(UnsupportedOperationException.class, () -> session.commandHistory().add(new NoOpCommand()));
        assertThrows(UnsupportedOperationException.class, () -> session.commandHistoryForUndo().add(new NoOpCommand()));
    }

    @Test
    void removeLatestCommandRemovesAndReturnsNewestCommand() {
        Session session = Session.create();
        Command first = new NoOpCommand();
        Command second = new NoOpCommand();
        session.record(first);
        session.record(second);

        assertEquals(second, session.removeLatestCommand().orElseThrow());
        assertEquals(List.of(first), session.commandHistory());
    }

    @Test
    void removeLatestCommandReturnsEmptyWhenHistoryIsEmpty() {
        assertTrue(Session.create().removeLatestCommand().isEmpty());
    }

    @Test
    void latestCommandReturnsNewestCommandWithoutRemovingIt() {
        Session session = Session.create();
        Command command = new NoOpCommand();
        session.record(command);

        assertEquals(command, session.latestCommand().orElseThrow());
        assertEquals(List.of(command), session.commandHistory());
    }

    @Test
    void selectedModelCanBeChangedAndCleared() {
        Session session = Session.create();

        session.selectModel("Example_Model");
        assertEquals("Example_Model", session.selectedModelAzName().orElseThrow());

        session.clearSelectedModel();
        assertFalse(session.selectedModelAzName().isPresent());
    }

    @Test
    void selectedModelAzNameUsesModelRootNameRules() {
        Session session = Session.create();

        session.selectModel("Example1");

        assertEquals("Example1", session.selectedModelAzName().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> session.selectModel("1Example"));
    }
}
