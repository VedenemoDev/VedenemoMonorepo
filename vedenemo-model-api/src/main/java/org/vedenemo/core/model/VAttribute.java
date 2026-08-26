package org.vedenemo.core.model;

import java.util.Objects;

public final class VAttribute extends Versionable {

    private final String azName;
    private final String visName;
    private final DataType type;
    private final String valueSetAzName;

    public VAttribute(String azName, String visName, DataType type, ModelVersion activeSince) {
        this(azName, visName, type, activeSince, null, null, null);
    }

    public VAttribute(
            String azName,
            String visName,
            DataType type,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
        this(azName, visName, type, activeSince, deprecatedSince, null, null);
    }

    public VAttribute(
            String azName,
            String visName,
            DataType type,
            ModelVersion activeSince,
            ModelVersion deprecatedSince,
            ModelVersion retiredSince
    ) {
        this(azName, visName, type, activeSince, deprecatedSince, retiredSince, null);
    }

    public VAttribute(
            String azName,
            String visName,
            DataType type,
            ModelVersion activeSince,
            ModelVersion deprecatedSince,
            ModelVersion retiredSince,
            String valueSetAzName
    ) {
        super(activeSince, deprecatedSince, retiredSince);
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.valueSetAzName = normalizeValueSetAzName(valueSetAzName);
    }

    public String azName() {
        return azName;
    }

    public String visName() {
        return visName;
    }

    public DataType type() {
        return type;
    }

    public String valueSetAzName() {
        return valueSetAzName;
    }

    public VAttribute withValueSetAzName(String valueSetAzName) {
        return new VAttribute(azName, visName, type, activeSince(), deprecatedSince().orElse(null), retiredSince().orElse(null), valueSetAzName);
    }

    public static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }

    private static String normalizeValueSetAzName(String valueSetAzName) {
        if (valueSetAzName == null || valueSetAzName.isBlank()) {
            return null;
        }
        return ModelTextRules.requireAzName(valueSetAzName);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VAttribute that)) {
            return false;
        }
        return azName.equals(that.azName)
                && visName.equals(that.visName)
                && type == that.type
                && Objects.equals(valueSetAzName, that.valueSetAzName)
                && activeSince().equals(that.activeSince())
                && deprecatedSince().equals(that.deprecatedSince())
                && retiredSince().equals(that.retiredSince());
    }

    @Override
    public int hashCode() {
        return Objects.hash(azName, visName, type, valueSetAzName, activeSince(), deprecatedSince(), retiredSince());
    }
}
