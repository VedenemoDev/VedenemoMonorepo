package org.vedenemo.core.spi.dump;

import java.time.Instant;
import java.util.Objects;

public record ModelInstanceDumpDescriptor(
        String key,
        String modelAzName,
        String modelVisName,
        String modelVersion,
        String rootVisName,
        int entityRecordCount,
        int associationLinkCount,
        Instant savedAt
) {

    public ModelInstanceDumpDescriptor {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("dump key must not be blank");
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
        rootVisName = rootVisName == null || rootVisName.isBlank() ? null : rootVisName.trim();
        if (entityRecordCount < 0) {
            throw new IllegalArgumentException("entity record count must not be negative");
        }
        if (associationLinkCount < 0) {
            throw new IllegalArgumentException("association link count must not be negative");
        }
        Objects.requireNonNull(savedAt, "savedAt must not be null");
    }
}
