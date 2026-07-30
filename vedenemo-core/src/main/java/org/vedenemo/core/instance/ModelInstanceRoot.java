package org.vedenemo.core.instance;

import java.util.Objects;

public record ModelInstanceRoot(String instanceRootId, String modelAzName, String modelVersion, String visName) {

    public ModelInstanceRoot {
        Objects.requireNonNull(instanceRootId, "instanceRootId must not be null");
        Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
    }
}
