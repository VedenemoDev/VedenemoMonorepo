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
        Cardinality cardinality,
        String sourceRoleName,
        String targetRoleName,
        Cardinality sourceCardinality,
        Cardinality targetCardinality
) implements Command {

    public CreateAssociationCommand(
            String modelAzName,
            AssociationKind kind,
            String associationAzName,
            String associationVisName,
            String sourceEntityAzName,
            String targetEntityAzName,
            Cardinality cardinality
    ) {
        this(modelAzName, kind, associationAzName, associationVisName, sourceEntityAzName, targetEntityAzName, cardinality, null, null, null, null);
    }

    public CreateAssociationCommand {
        modelAzName = requireModelAzName(modelAzName);
        kind = Objects.requireNonNull(kind, "kind must not be null");
        associationAzName = requireAssociationAzName(associationAzName);
        associationVisName = requireVisName(associationVisName);
        sourceEntityAzName = requireEntityAzName(sourceEntityAzName);
        targetEntityAzName = requireEntityAzName(targetEntityAzName);
        if (kind == AssociationKind.RELATION) {
            sourceRoleName = requireRoleName(sourceRoleName, "sourceRoleName");
            targetRoleName = requireRoleName(targetRoleName, "targetRoleName");
            if (sourceRoleName.equals(targetRoleName)) {
                throw new IllegalArgumentException("relation end role names should be different");
            }
            sourceCardinality = Objects.requireNonNull(sourceCardinality, "sourceCardinality must not be null");
            targetCardinality = Objects.requireNonNull(targetCardinality, "targetCardinality must not be null");
            cardinality = targetCardinality;
        } else {
            cardinality = Objects.requireNonNull(cardinality, "cardinality must not be null");
            sourceRoleName = null;
            targetRoleName = null;
            sourceCardinality = null;
            targetCardinality = cardinality;
        }
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

    private static String requireRoleName(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
