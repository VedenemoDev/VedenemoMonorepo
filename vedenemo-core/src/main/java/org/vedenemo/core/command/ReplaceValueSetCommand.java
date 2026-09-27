package org.vedenemo.core.command;

import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ValueSet;
import org.vedenemo.core.model.ValueSetEntry;

import java.util.List;
import java.util.Objects;

public record ReplaceValueSetCommand(
        String modelAzName,
        String valueSetAzName,
        DataType dataType,
        List<ValueSetEntry> entries,
        List<ValueSetEntry> previousEntries
) implements Command {

    public ReplaceValueSetCommand(
            String modelAzName,
            String valueSetAzName,
            DataType dataType,
            List<ValueSetEntry> entries
    ) {
        this(modelAzName, valueSetAzName, dataType, entries, List.of());
    }

    public ReplaceValueSetCommand {
        ModelRoot.uniquenessKey(modelAzName);
        ValueSet.uniquenessKey(valueSetAzName);
        dataType = Objects.requireNonNull(dataType, "dataType must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        previousEntries = List.copyOf(Objects.requireNonNull(previousEntries, "previousEntries must not be null"));
    }
}
