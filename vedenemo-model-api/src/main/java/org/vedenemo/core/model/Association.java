package org.vedenemo.core.model;

import java.util.Optional;

public sealed interface Association permits OwnershipAssociation, ReferenceAssociation {

    String azName();

    String visName();

    String sourceEntityAzName();

    String targetEntityAzName();

    Cardinality cardinality();

    ModelVersion activeSince();

    Optional<ModelVersion> deprecatedSince();

    AssociationKind kind();

    static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }
}
