package org.vedenemo.console;

public record AttributeSummary(
        String azName,
        String visName,
        String dataType,
        String activeSince,
        String deprecatedSince,
        String retiredSince
) {
    public AttributeSummary(String azName, String visName, String dataType, String activeSince, String deprecatedSince) {
        this(azName, visName, dataType, activeSince, deprecatedSince, null);
    }
}
