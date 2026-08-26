package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;

public record SetAttributeValueSetCommand(
        String modelAzName,
        String entityAzName,
        String attributeAzName,
        String valueSetAzName
) implements Command {

    public SetAttributeValueSetCommand {
        ModelRoot.uniquenessKey(modelAzName);
        VEntity.uniquenessKey(entityAzName);
        VAttribute.uniquenessKey(attributeAzName);
        ValueSet.uniquenessKey(valueSetAzName);
    }
}
