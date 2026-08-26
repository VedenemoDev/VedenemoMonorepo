package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;

record ClearAttributeValueSetCommand(
        String modelAzName,
        String entityAzName,
        String attributeAzName
) implements Command {

    ClearAttributeValueSetCommand {
        ModelRoot.uniquenessKey(modelAzName);
        VEntity.uniquenessKey(entityAzName);
        VAttribute.uniquenessKey(attributeAzName);
    }
}
