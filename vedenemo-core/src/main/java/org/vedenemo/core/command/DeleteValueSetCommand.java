package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ValueSet;

record DeleteValueSetCommand(String modelAzName, String valueSetAzName) implements Command {

    DeleteValueSetCommand {
        ModelRoot.uniquenessKey(modelAzName);
        ValueSet.uniquenessKey(valueSetAzName);
    }
}
