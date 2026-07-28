package org.vedenemo.console;

public record SnapshotSummary(
        String key,
        String modelAzName,
        String modelVisName,
        String modelVersion,
        int commandCount,
        String savedAt
) {
}

