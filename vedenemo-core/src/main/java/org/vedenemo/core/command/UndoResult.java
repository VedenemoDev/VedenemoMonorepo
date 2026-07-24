package org.vedenemo.core.command;

import java.util.Objects;

public record UndoResult(
        Status status,
        String undoneCommand,
        String modelAzName,
        String entityAzName,
        String attributeAzName,
        String associationAzName
) {

    public static final UndoResult NOTHING_TO_UNDO = new UndoResult(Status.NOTHING_TO_UNDO, null, null, null, null, null);

    public UndoResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.UNDONE) {
            Objects.requireNonNull(undoneCommand, "undoneCommand must not be null");
            Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        }
    }

    public static UndoResult undoneCreateEntity(String modelAzName, String entityAzName) {
        return new UndoResult(
                Status.UNDONE,
                "create-entity",
                modelAzName,
                Objects.requireNonNull(entityAzName, "entityAzName must not be null"),
                null,
                null
        );
    }

    public static UndoResult undoneCreateAttribute(String modelAzName, String entityAzName, String attributeAzName) {
        return new UndoResult(
                Status.UNDONE,
                "create-attribute",
                modelAzName,
                Objects.requireNonNull(entityAzName, "entityAzName must not be null"),
                Objects.requireNonNull(attributeAzName, "attributeAzName must not be null"),
                null
        );
    }

    public static UndoResult undoneCreateAssociation(String modelAzName, String associationAzName) {
        return new UndoResult(
                Status.UNDONE,
                "create-association",
                modelAzName,
                null,
                null,
                Objects.requireNonNull(associationAzName, "associationAzName must not be null")
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
