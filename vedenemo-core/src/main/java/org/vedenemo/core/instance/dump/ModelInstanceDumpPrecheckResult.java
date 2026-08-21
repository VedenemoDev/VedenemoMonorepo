package org.vedenemo.core.instance.dump;

import java.util.List;

public record ModelInstanceDumpPrecheckResult(
        boolean importable,
        boolean confirmationRequired,
        List<String> warnings,
        List<String> diagnostics
) {

    public ModelInstanceDumpPrecheckResult {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
