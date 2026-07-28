package org.vedenemo.core.spi.snapshot;

import java.util.Objects;

public record SnapshotContent(SnapshotDescriptor descriptor, String content) {

    public SnapshotContent {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}

