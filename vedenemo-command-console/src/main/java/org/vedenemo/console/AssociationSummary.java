package org.vedenemo.console;

public record AssociationSummary(
        String azName,
        String visName,
        String kind,
        String sourceEntityAzName,
        String targetEntityAzName,
        String cardinality,
        String activeSince,
        String deprecatedSince
) {
}
