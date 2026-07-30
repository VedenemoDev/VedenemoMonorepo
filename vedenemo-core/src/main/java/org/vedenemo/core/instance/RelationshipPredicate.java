package org.vedenemo.core.instance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RelationshipPredicate(
        String associationAzName,
        RelationshipDirection direction,
        String entityAzName,
        Map<String, Object> equals
) {

    public RelationshipPredicate {
        Objects.requireNonNull(associationAzName, "associationAzName must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(entityAzName, "entityAzName must not be null");
        equals = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(equals, "equals must not be null")));
    }
}
