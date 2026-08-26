package org.vedenemo.core.command;

import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;

import java.util.Objects;

public record CreateAttributeCommand(
        String modelAzName,
        String entityAzName,
        String attributeAzName,
        String attributeVisName,
        DataType dataType,
        String valueSetAzName
) implements Command {

    public CreateAttributeCommand(
            String modelAzName,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            DataType dataType
    ) {
        this(modelAzName, entityAzName, attributeAzName, attributeVisName, dataType, null);
    }

    public CreateAttributeCommand {
        modelAzName = requireModelAzName(modelAzName);
        entityAzName = requireEntityAzName(entityAzName);
        attributeAzName = requireAttributeAzName(attributeAzName);
        attributeVisName = requireVisName(attributeVisName);
        dataType = Objects.requireNonNull(dataType, "dataType must not be null");
        valueSetAzName = normalizeValueSetAzName(valueSetAzName);
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

    private static String requireVisName(String value) {
        Objects.requireNonNull(value, "attributeVisName must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("attributeVisName must not be blank");
        }
        return value;
    }

    private static String normalizeValueSetAzName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        ValueSet.uniquenessKey(value);
        return value;
    }
}
