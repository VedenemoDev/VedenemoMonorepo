package org.vedenemo.cli;

public record AttributeSummary(
        String azName,
        String visName,
        String dataType,
        String activeSince,
        String deprecatedSince
) {
}
