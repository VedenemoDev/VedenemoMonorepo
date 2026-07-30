package org.vedenemo.core.instance;

import java.util.Objects;
import java.util.UUID;

public record AssociationInstanceLink(
        String id,
        String modelAzName,
        String associationAzName,
        InstanceId sourceInstanceId,
        InstanceId targetInstanceId
) {

    public AssociationInstanceLink {
        Objects.requireNonNull(id, "id must not be null");
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("link id must be a UUID string", exception);
        }
        Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        Objects.requireNonNull(associationAzName, "associationAzName must not be null");
        Objects.requireNonNull(sourceInstanceId, "sourceInstanceId must not be null");
        Objects.requireNonNull(targetInstanceId, "targetInstanceId must not be null");
    }

    public static AssociationInstanceLink create(
            String modelAzName,
            String associationAzName,
            InstanceId sourceInstanceId,
            InstanceId targetInstanceId
    ) {
        return new AssociationInstanceLink(
                UUID.randomUUID().toString(),
                modelAzName,
                associationAzName,
                sourceInstanceId,
                targetInstanceId
        );
    }
}
