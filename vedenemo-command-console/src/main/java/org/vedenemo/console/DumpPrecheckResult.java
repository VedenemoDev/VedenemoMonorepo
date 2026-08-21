package org.vedenemo.console;

import java.util.List;

public record DumpPrecheckResult(
        boolean importable,
        boolean confirmationRequired,
        List<String> warnings,
        List<String> diagnostics
) {

    public DumpPrecheckResult {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
