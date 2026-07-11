package org.vedenemo.core.model;

import java.util.Objects;

public final class VAttribute extends Versionable {

    private final String azName;
    private final String visName;
    private final DataType type;

    public VAttribute(String azName, String visName, DataType type, ModelVersion activeSince) {
        this(azName, visName, type, activeSince, null);
    }

    public VAttribute(
            String azName,
            String visName,
            DataType type,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
        super(activeSince, deprecatedSince);
        this.azName = ModelTextRules.requireAzName(azName);
        this.visName = ModelTextRules.requireVisName(visName);
        this.type = Objects.requireNonNull(type, "type must not be null");
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

    static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
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
                && activeSince().equals(that.activeSince())
                && deprecatedSince().equals(that.deprecatedSince());
    }

    @Override
    public int hashCode() {
        return Objects.hash(azName, visName, type, activeSince(), deprecatedSince());
    }
}
