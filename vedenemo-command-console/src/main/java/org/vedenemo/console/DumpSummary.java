package org.vedenemo.console;

public record DumpSummary(
        String key,
        String modelAzName,
        String modelVisName,
        String modelVersion,
        String rootVisName,
        int entityRecordCount,
        int associationLinkCount,
        String savedAt
) {
}
