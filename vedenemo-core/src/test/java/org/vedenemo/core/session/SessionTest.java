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

        assertThrows(IllegalArgumentException.class, () -> session.selectModel("Example1"));
    }
}
