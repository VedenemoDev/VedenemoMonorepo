package org.vedenemo.core.model;

import java.util.Objects;

public record ModelVersion(int major, int minor, int patch) {

    public ModelVersion {
        requireNonNegative("major", major);
        requireNonNegative("minor", minor);
        requireNonNegative("patch", patch);
    }

    public static ModelVersion parse(String value) {
        Objects.requireNonNull(value, "version must not be null");
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("version must use major.minor.patch format");
        }
        return new ModelVersion(parsePart("major", parts[0]), parsePart("minor", parts[1]), parsePart("patch", parts[2]));
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int parsePart(String name, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " version part must not be blank");
        }
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException(name + " version part must contain only digits");
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " version part is too large", exception);
        }
    }

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " version part must be non-negative");
        }
    }
}
