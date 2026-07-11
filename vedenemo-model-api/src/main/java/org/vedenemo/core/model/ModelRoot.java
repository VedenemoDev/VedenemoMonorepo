package org.vedenemo.core.model;

import java.util.Objects;

public record ModelRoot(String azName, String visName, ModelVersion version) {

    public ModelRoot {
        azName = ModelTextRules.requireAzName(azName);
        visName = ModelTextRules.requireVisName(visName);
        version = Objects.requireNonNull(version, "version must not be null");
    }

    public static ModelRoot create(String azName, String visName, String version) {
        return new ModelRoot(azName, visName, ModelVersion.parse(version));
    }

    public static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }
}
