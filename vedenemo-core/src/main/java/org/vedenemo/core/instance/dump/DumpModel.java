package org.vedenemo.core.instance.dump;

public record DumpModel(String azName, String visName, String version) {

    public DumpModel {
        azName = requireText(azName, "model azName");
        visName = requireText(visName, "model visible name");
        version = requireText(version, "model version");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
