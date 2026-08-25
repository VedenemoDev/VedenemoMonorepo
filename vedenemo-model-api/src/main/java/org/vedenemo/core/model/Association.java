package org.vedenemo.core.model;

import java.util.Optional;

public sealed interface Association permits OwnershipAssociation, ReferenceAssociation, RelationAssociation {

    String azName();

    String visName();

    String sourceEntityAzName();

    String targetEntityAzName();

    Cardinality cardinality();

    default String sourceRoleName() {
        return null;
    }

    default String targetRoleName() {
        return null;
    }

    default Cardinality sourceCardinality() {
        return null;
    }

    default Cardinality targetCardinality() {
        return cardinality();
    }

    ModelVersion activeSince();

    Optional<ModelVersion> deprecatedSince();

    Optional<ModelVersion> retiredSince();

    AssociationKind kind();

    static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }
}
