package org.vedenemo.core.command;

import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;

record DeleteAssociationCommand(String modelAzName, String associationAzName) implements Command {

    DeleteAssociationCommand {
        modelAzName = requireModelAzName(modelAzName);
        associationAzName = requireAssociationAzName(associationAzName);
    }

    private static String requireModelAzName(String value) {
        ModelRoot.uniquenessKey(value);
        return value;
    }

    private static String requireAssociationAzName(String value) {
        Association.uniquenessKey(value);
        return value;
    }
}
