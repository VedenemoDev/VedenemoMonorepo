package org.vedenemo.core.instance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

public record EntityInstance(
        InstanceId id,
        String modelAzName,
        String modelVersion,
        String entityAzName,
        Map<String, InstanceValue> values
) {

    public EntityInstance {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        Objects.requireNonNull(entityAzName, "entityAzName must not be null");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values must not be null")));
    }
}
