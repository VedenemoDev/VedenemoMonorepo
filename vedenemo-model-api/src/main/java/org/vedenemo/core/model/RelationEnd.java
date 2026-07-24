package org.vedenemo.core.model;

import java.util.Objects;

public record RelationEnd(String entityAzName, String roleName, Cardinality cardinality) {

    public RelationEnd {
        VEntity.uniquenessKey(entityAzName);
        roleName = requireRoleName(roleName);
        cardinality = Objects.requireNonNull(cardinality, "cardinality must not be null");
    }

    private static String requireRoleName(String value) {
        Objects.requireNonNull(value, "roleName must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
        return value;
    }
}
