package org.vedenemo.core.script;

import java.util.Objects;

public record VedenemoScriptImportResult(String modelAzName, int commandCount) {

    public VedenemoScriptImportResult {
        Objects.requireNonNull(modelAzName, "modelAzName must not be null");
        if (commandCount < 0) {
            throw new IllegalArgumentException("commandCount must not be negative");
        }
    }
}
