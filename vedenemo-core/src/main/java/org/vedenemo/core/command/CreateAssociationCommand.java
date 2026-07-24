package org.vedenemo.core.command;

import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;

import java.util.Objects;

public record CreateAssociationCommand(
        String modelAzName,
        AssociationKind kind,
        String associationAzName,
        String associationVisName,
        String sourceEntityAzName,
        String targetEntityAzName,
        Cardinality cardinality
) implements Command {

    public CreateAssociationCommand {
        modelAzName = requireModelAzName(modelAzName);
        kind = Objects.requireNonNull(kind, "kind must not be null");
        associationAzName = requireAssociationAzName(associationAzName);
        associationVisName = requireVisName(associationVisName);
        sourceEntityAzName = requireEntityAzName(sourceEntityAzName);
        targetEntityAzName = requireEntityAzName(targetEntityAzName);
        cardinality = Objects.requireNonNull(cardinality, "cardinality must not be null");
    }

    private static String requireModelAzName(String value) {
        ModelRoot.uniquenessKey(value);
        return value;
    }

    private static String requireAssociationAzName(String value) {
        Association.uniquenessKey(value);
        return value;
    }

    private static String requireEntityAzName(String value) {
        VEntity.uniquenessKey(value);
        return value;
    }

    private static String requireVisName(String value) {
        Objects.requireNonNull(value, "associationVisName must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("associationVisName must not be blank");
        }
        return value;
    }
}
