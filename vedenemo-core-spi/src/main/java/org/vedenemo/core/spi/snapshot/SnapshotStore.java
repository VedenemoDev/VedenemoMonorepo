package org.vedenemo.core.spi.snapshot;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface SnapshotStore {

    List<SnapshotDescriptor> listSnapshots(String scope) throws IOException;

    Optional<SnapshotContent> readSnapshot(String scope, String snapshotKey) throws IOException;

    SnapshotDescriptor writeSnapshot(
            String scope,
            String modelAzName,
            String snapshotName,
            String content,
            SnapshotDescriptor descriptor
    ) throws IOException;
}

