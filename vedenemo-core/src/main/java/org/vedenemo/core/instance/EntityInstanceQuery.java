package org.vedenemo.core.instance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EntityInstanceQuery(
        Map<String, Object> equals,
        List<RelationshipPredicate> relationships
) {

    public EntityInstanceQuery {
        equals = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(equals, "equals must not be null")));
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships must not be null"));
    }

    public static EntityInstanceQuery attributeEquals(Map<String, Object> equals) {
        return new EntityInstanceQuery(equals, List.of());
    }
}
