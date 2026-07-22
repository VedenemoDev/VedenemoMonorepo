package org.vedenemo.console;

public record AttributeSummary(
        String azName,
        String visName,
        String dataType,
        String activeSince,
        String deprecatedSince
) {
}
