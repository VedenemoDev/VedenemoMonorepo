package org.vedenemo.core.instance.dump;

public record DumpRoot(String sourceInstanceRootId, String visName) {

    public DumpRoot {
        sourceInstanceRootId = requireText(sourceInstanceRootId, "source instance root id");
        visName = visName == null || visName.isBlank() ? null : visName.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
