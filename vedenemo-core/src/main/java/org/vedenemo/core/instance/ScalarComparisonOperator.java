package org.vedenemo.core.instance;

import java.util.Locale;

public enum ScalarComparisonOperator {
    EQUALS,
    LESS_THAN,
    GREATER_THAN,
    CONTAINS;

    public static ScalarComparisonOperator parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("comparison operator is required");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "=", "equals", "eq" -> EQUALS;
            case "<", "less_than", "lt" -> LESS_THAN;
            case ">", "greater_than", "gt" -> GREATER_THAN;
            case "contains" -> CONTAINS;
            default -> throw new IllegalArgumentException("unsupported comparison operator: " + value);
        };
    }
}
