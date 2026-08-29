package org.vedenemo.console;

public record AttributeSummary(
        String azName,
        String visName,
        String dataType,
        boolean required,
        String valueSetAzName,
        String activeSince,
        String deprecatedSince,
        String retiredSince
) {
    public AttributeSummary(String azName, String visName, String dataType, String activeSince, String deprecatedSince) {
        this(azName, visName, dataType, false, null, activeSince, deprecatedSince, null);
    }

    public AttributeSummary(String azName, String visName, String dataType, String activeSince, String deprecatedSince, String retiredSince) {
        this(azName, visName, dataType, false, null, activeSince, deprecatedSince, retiredSince);
    }
}
