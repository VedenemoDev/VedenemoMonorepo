package org.vedenemo.core.model;

import java.util.Objects;

public final class RelationAssociation extends Versionable implements Association {

    private final String azName;
    private final String visName;
    private final RelationEnd sourceEnd;
    private final RelationEnd targetEnd;

    public RelationAssociation(
            String azName,
            String visName,
            RelationEnd sourceEnd,
            RelationEnd targetEnd,
            ModelVersion activeSince
    ) {
        this(azName, visName, sourceEnd, targetEnd, activeSince, null);
    }

    public RelationAssociation(
            String azName,
            String visName,
            RelationEnd sourceEnd,
            RelationEnd targetEnd,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
        super(activeSince, deprecatedSince);
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
        this.sourceEnd = Objects.requireNonNull(sourceEnd, "sourceEnd must not be null");
        this.targetEnd = Objects.requireNonNull(targetEnd, "targetEnd must not be null");
        if (sourceEnd.roleName().equals(targetEnd.roleName())) {
            throw new IllegalArgumentException("relation end role names should be different");
        }
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
        return sourceEnd.entityAzName();
    }

    @Override
    public String targetEntityAzName() {
        return targetEnd.entityAzName();
    }

    @Override
    public Cardinality cardinality() {
        return targetEnd.cardinality();
    }

    @Override
    public String sourceRoleName() {
        return sourceEnd.roleName();
    }

    @Override
    public String targetRoleName() {
        return targetEnd.roleName();
    }

    @Override
    public Cardinality sourceCardinality() {
        return sourceEnd.cardinality();
    }

    @Override
    public Cardinality targetCardinality() {
        return targetEnd.cardinality();
    }

    @Override
    public AssociationKind kind() {
        return AssociationKind.RELATION;
    }

    public RelationEnd sourceEnd() {
        return sourceEnd;
    }

    public RelationEnd targetEnd() {
        return targetEnd;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RelationAssociation that)) {
            return false;
        }
        return azName.equals(that.azName)
                && visName.equals(that.visName)
                && sourceEnd.equals(that.sourceEnd)
                && targetEnd.equals(that.targetEnd)
                && activeSince().equals(that.activeSince())
                && deprecatedSince().equals(that.deprecatedSince());
    }

    @Override
    public int hashCode() {
        return Objects.hash(azName, visName, sourceEnd, targetEnd, activeSince(), deprecatedSince());
    }
}
