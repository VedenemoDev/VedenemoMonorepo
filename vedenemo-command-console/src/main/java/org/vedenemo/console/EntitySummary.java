package org.vedenemo.console;

public record EntitySummary(
        String azName,
        String visName,
        String activeSince,
        String deprecatedSince,
        String retiredSince
) {
    public EntitySummary(String azName, String visName, String activeSince, String deprecatedSince) {
        this(azName, visName, activeSince, deprecatedSince, null);
    }
}
