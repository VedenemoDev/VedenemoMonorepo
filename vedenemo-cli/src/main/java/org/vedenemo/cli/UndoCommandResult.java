package org.vedenemo.cli;

import java.util.Objects;

public record UndoCommandResult(
        Status status,
        String undoneCommand,
        String modelAzName,
        String entityAzName,
        String attributeAzName
) {

    public static final UndoCommandResult NOTHING_TO_UNDO = new UndoCommandResult(Status.NOTHING_TO_UNDO, null, null, null, null);

    public UndoCommandResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static UndoCommandResult undone(
            String undoneCommand,
            String modelAzName,
            String entityAzName,
            String attributeAzName
    ) {
        return new UndoCommandResult(
                Status.UNDONE,
                Objects.requireNonNull(undoneCommand, "undoneCommand must not be null"),
                Objects.requireNonNull(modelAzName, "modelAzName must not be null"),
                entityAzName,
                attributeAzName
        );
    }

    public boolean isNothingToUndo() {
        return status == Status.NOTHING_TO_UNDO;
    }

    public enum Status {
        UNDONE,
        NOTHING_TO_UNDO
    }
}
