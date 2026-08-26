package org.vedenemo.core.command;

import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ValueSet;
import org.vedenemo.core.model.ValueSetEntry;

import java.util.List;
import java.util.Objects;

public record CreateValueSetCommand(
        String modelAzName,
        String valueSetAzName,
        DataType dataType,
        List<ValueSetEntry> entries
) implements Command {

    public CreateValueSetCommand {
        ModelRoot.uniquenessKey(modelAzName);
        ValueSet.uniquenessKey(valueSetAzName);
        dataType = Objects.requireNonNull(dataType, "dataType must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }
}
