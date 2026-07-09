package org.vedenemo.core.model;

import java.util.Locale;
import java.util.Objects;

public record ModelRoot(String azName, String visName, ModelVersion version) {

    public ModelRoot {
        azName = requireAzName(azName);
        visName = requireVisName(visName);
        version = Objects.requireNonNull(version, "version must not be null");
    }

    public static ModelRoot create(String azName, String visName, String version) {
        return new ModelRoot(azName, visName, ModelVersion.parse(version));
    }

    public static String uniquenessKey(String azName) {
        return requireAzName(azName).toLowerCase(Locale.ROOT);
    }

    private static String requireAzName(String azName) {
        Objects.requireNonNull(azName, "azName must not be null");
        if (azName.isBlank()) {
            throw new IllegalArgumentException("azName must not be blank");
        }
        if (!isAsciiLetter(azName.charAt(0))) {
            throw new IllegalArgumentException("azName must start with an ASCII letter");
        }
        for (int index = 1; index < azName.length(); index++) {
            char value = azName.charAt(index);
            if (!isAsciiLetter(value) && value != '_') {
                throw new IllegalArgumentException("azName must contain only ASCII letters and underscores");
            }
        }
        return azName;
    }

    private static String requireVisName(String visName) {
        Objects.requireNonNull(visName, "visName must not be null");
        if (visName.isBlank()) {
            throw new IllegalArgumentException("visName must not be blank");
        }
        return visName;
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }
}
