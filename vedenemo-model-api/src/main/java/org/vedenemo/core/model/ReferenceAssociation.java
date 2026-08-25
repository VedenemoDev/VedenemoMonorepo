package org.vedenemo.core.model;

import java.util.Objects;

public final class ReferenceAssociation extends Versionable implements Association {

    private final String azName;
    private final String visName;
    private final String sourceEntityAzName;
    private final String targetEntityAzName;
    private final Cardinality cardinality;

    public ReferenceAssociation(
            String azName,
            String visName,
            String sourceEntityAzName,
            String targetEntityAzName,
            Cardinality cardinality,
            ModelVersion activeSince
    ) {
        this(azName, visName, sourceEntityAzName, targetEntityAzName, cardinality, activeSince, null);
    }

    public ReferenceAssociation(
            String azName,
            String visName,
            String sourceEntityAzName,
            String targetEntityAzName,
            Cardinality cardinality,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
        this(azName, visName, sourceEntityAzName, targetEntityAzName, cardinality, activeSince, deprecatedSince, null);
    }

    public ReferenceAssociation(
            String azName,
            String visName,
            String sourceEntityAzName,
            String targetEntityAzName,
            Cardinality cardinality,
            ModelVersion activeSince,
            ModelVersion deprecatedSince,
            ModelVersion retiredSince
    ) {
        super(activeSince, deprecatedSince, retiredSince);
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
        VEntity.uniquenessKey(sourceEntityAzName);
        VEntity.uniquenessKey(targetEntityAzName);
        this.sourceEntityAzName = sourceEntityAzName;
        this.targetEntityAzName = targetEntityAzName;
        this.cardinality = Objects.requireNonNull(cardinality, "cardinality must not be null");
    }

    @Override
    public String azName() {
        return azName;
    }

    @Override
    public String visName() {
        return visName;
    }

    @Override
    public String sourceEntityAzName() {
        return sourceEntityAzName;
    }

    @Override
    public String targetEntityAzName() {
        return targetEntityAzName;
    }

    @Override
    public Cardinality cardinality() {
        return cardinality;
    }

    @Override
    public AssociationKind kind() {
        return AssociationKind.REFERENCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReferenceAssociation that)) {
            return false;
        }
        return azName.equals(that.azName)
                && visName.equals(that.visName)
                && sourceEntityAzName.equals(that.sourceEntityAzName)
                && targetEntityAzName.equals(that.targetEntityAzName)
                && cardinality.equals(that.cardinality)
                && activeSince().equals(that.activeSince())
                && deprecatedSince().equals(that.deprecatedSince())
                && retiredSince().equals(that.retiredSince());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                azName,
                visName,
                sourceEntityAzName,
                targetEntityAzName,
                cardinality,
                activeSince(),
                deprecatedSince(),
                retiredSince()
        );
    }
}
