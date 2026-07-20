package org.vedenemo.core.model;

import java.util.Locale;
import java.util.Objects;

final class ModelTextRules {

    private ModelTextRules() {
    }

    static String requireAzName(String azName) {
        Objects.requireNonNull(azName, "azName must not be null");
        if (azName.isBlank()) {
            throw new IllegalArgumentException("azName must not be blank");
        }
        if (!isAsciiLetter(azName.charAt(0))) {
            throw new IllegalArgumentException("azName must start with an ASCII letter");
        }
        for (int index = 1; index < azName.length(); index++) {
            char value = azName.charAt(index);
            if (!isAsciiLetter(value) && !isAsciiDigit(value) && value != '_') {
                throw new IllegalArgumentException("azName must contain only ASCII letters, digits, and underscores after the first character");
            }
        }
        return azName;
    }

    static String requireVisName(String visName) {
        Objects.requireNonNull(visName, "visName must not be null");
        if (visName.isBlank()) {
            throw new IllegalArgumentException("visName must not be blank");
        }
        return visName;
    }

    static String uniquenessKey(String azName) {
        return requireAzName(azName).toLowerCase(Locale.ROOT);
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }
}
