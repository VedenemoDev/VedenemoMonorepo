package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;

record DeleteAttributeCommand(String modelAzName, String entityAzName, String attributeAzName) implements Command {

    DeleteAttributeCommand {
        modelAzName = requireModelAzName(modelAzName);
        entityAzName = requireEntityAzName(entityAzName);
        attributeAzName = requireAttributeAzName(attributeAzName);
    }

    private static String requireModelAzName(String value) {
        ModelRoot.uniquenessKey(value);
        return value;
    }

    private static String requireEntityAzName(String value) {
        VEntity.uniquenessKey(value);
        return value;
    }

    private static String requireAttributeAzName(String value) {
        VAttribute.uniquenessKey(value);
        return value;
    }
}
