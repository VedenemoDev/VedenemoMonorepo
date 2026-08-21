package org.vedenemo.console;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DumpImportResult(
        ModelInstanceRootSummary root,
        Map<String, Integer> createdEntityCounts,
        int createdAssociationLinkCount,
        int skippedDuplicateLinkCount,
        List<String> warnings,
        List<String> failedInserts
) {

    public DumpImportResult {
        createdEntityCounts = new LinkedHashMap<>(createdEntityCounts == null ? Map.of() : createdEntityCounts);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        failedInserts = List.copyOf(failedInserts == null ? List.of() : failedInserts);
    }
}
