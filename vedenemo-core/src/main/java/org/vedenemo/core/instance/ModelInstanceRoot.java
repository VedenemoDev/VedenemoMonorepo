package org.vedenemo.core.instance;

import java.util.Objects;

public record ModelInstanceRoot(String modelAzName, String modelVersion, String visName) {

    public ModelInstanceRoot {
        Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        Objects.requireNonNull(visName, "visName must not be null");
    }
}
