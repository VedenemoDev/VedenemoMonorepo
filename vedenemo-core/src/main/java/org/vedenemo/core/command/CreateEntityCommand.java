package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;

import java.util.Objects;

public record CreateEntityCommand(String modelAzName, String entityAzName, String entityVisName) implements Command {

    public CreateEntityCommand {
        modelAzName = requireModelAzName(modelAzName);
        entityAzName = requireEntityAzName(entityAzName);
        entityVisName = requireVisName(entityVisName);
    }

    private static String requireModelAzName(String value) {
        ModelRoot.uniquenessKey(value);
        return value;
    }

    private static String requireEntityAzName(String value) {
        VEntity.uniquenessKey(value);
        return value;
    }

    private static String requireVisName(String value) {
        Objects.requireNonNull(value, "entityVisName must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("entityVisName must not be blank");
        }
        return value;
    }
}
