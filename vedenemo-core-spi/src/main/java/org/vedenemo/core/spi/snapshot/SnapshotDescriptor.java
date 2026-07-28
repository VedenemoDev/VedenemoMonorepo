package org.vedenemo.core.spi.snapshot;

import java.time.Instant;
import java.util.Objects;

public record SnapshotDescriptor(
        String key,
        String modelAzName,
        String modelVisName,
        String modelVersion,
        int commandCount,
        Instant savedAt
) {

    public SnapshotDescriptor {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("snapshot key must not be blank");
        }
        if (modelAzName == null || modelAzName.isBlank()) {
            throw new IllegalArgumentException("model azName must not be blank");
        }
        if (modelVisName == null || modelVisName.isBlank()) {
            throw new IllegalArgumentException("model visible name must not be blank");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("model version must not be blank");
        }
        if (commandCount < 0) {
            throw new IllegalArgumentException("command count must not be negative");
        }
        Objects.requireNonNull(savedAt, "savedAt must not be null");
    }
}

