package org.vedenemo.core.instance;

import java.util.Objects;

public record ScalarComparison(
        String attributeAzName,
        ScalarComparisonOperator operator,
        Object value
) {

    public ScalarComparison {
        Objects.requireNonNull(attributeAzName, "attributeAzName must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ScalarComparison equals(String attributeAzName, Object value) {
        return new ScalarComparison(attributeAzName, ScalarComparisonOperator.EQUALS, value);
    }
}
