package org.vedenemo.core.instance.dump;

import org.vedenemo.core.instance.ModelInstanceRoot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ModelInstanceDumpImportResult(
        ModelInstanceRoot root,
        Map<String, Integer> createdEntityCounts,
        int createdAssociationLinkCount,
        int skippedDuplicateLinkCount,
        List<String> warnings,
        List<String> failedInserts
) {

    public ModelInstanceDumpImportResult {
        Objects.requireNonNull(root, "root must not be null");
        createdEntityCounts = new LinkedHashMap<>(Objects.requireNonNull(createdEntityCounts, "createdEntityCounts must not be null"));
        if (createdAssociationLinkCount < 0) {
            throw new IllegalArgumentException("created association-link count must not be negative");
        }
        if (skippedDuplicateLinkCount < 0) {
            throw new IllegalArgumentException("skipped duplicate-link count must not be negative");
        }
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        failedInserts = List.copyOf(failedInserts == null ? List.of() : failedInserts);
    }
}
