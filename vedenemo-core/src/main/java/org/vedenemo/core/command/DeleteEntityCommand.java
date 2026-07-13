package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;

record DeleteEntityCommand(String modelAzName, String entityAzName) implements Command {

    DeleteEntityCommand {
        modelAzName = requireModelAzName(modelAzName);
        entityAzName = requireEntityAzName(entityAzName);
    }

    private static String requireModelAzName(String value) {
        ModelRoot.uniquenessKey(value);
        return value;
    }

    private static String requireEntityAzName(String value) {
        VEntity.uniquenessKey(value);
        return value;
    }
}
