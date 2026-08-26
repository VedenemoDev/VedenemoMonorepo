package org.vedenemo.core.command;

import org.vedenemo.core.model.ModelRoot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModelCommandJournal {

    private final Map<String, List<Command>> commandsByModelAzName = new LinkedHashMap<>();

    public synchronized void record(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        String modelAzName = modelAzName(command);
        if (modelAzName == null) {
            return;
        }
        commandsByModelAzName
                .computeIfAbsent(ModelRoot.uniquenessKey(modelAzName), ignored -> new ArrayList<>())
                .add(command);
    }

    public synchronized List<Command> listForModel(String modelAzName) {
        List<Command> commands = commandsByModelAzName.get(ModelRoot.uniquenessKey(modelAzName));
        if (commands == null) {
            return List.of();
        }
        return List.copyOf(commands);
    }

    public synchronized void replaceForModel(String modelAzName, List<Command> commands) {
        Objects.requireNonNull(commands, "commands must not be null");
        ArrayList<Command> copy = new ArrayList<>(commands);
        commandsByModelAzName.put(ModelRoot.uniquenessKey(modelAzName), copy);
    }

    public synchronized void removeLatest(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        String modelAzName = modelAzName(command);
        if (modelAzName == null) {
            return;
        }
        List<Command> commands = commandsByModelAzName.get(ModelRoot.uniquenessKey(modelAzName));
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (int index = commands.size() - 1; index >= 0; index--) {
            if (commands.get(index).equals(command)) {
                commands.remove(index);
                return;
            }
        }
    }

    private static String modelAzName(Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            return createEntityCommand.modelAzName();
        }
        if (command instanceof CreateAttributeCommand createAttributeCommand) {
            return createAttributeCommand.modelAzName();
        }
        if (command instanceof CreateAssociationCommand createAssociationCommand) {
            return createAssociationCommand.modelAzName();
        }
        if (command instanceof CreateValueSetCommand createValueSetCommand) {
            return createValueSetCommand.modelAzName();
        }
        if (command instanceof SetAttributeValueSetCommand setAttributeValueSetCommand) {
            return setAttributeValueSetCommand.modelAzName();
        }
        if (command instanceof DeleteAssociationCommand deleteAssociationCommand) {
            return deleteAssociationCommand.modelAzName();
        }
        if (command instanceof DeleteEntityCommand deleteEntityCommand) {
            return deleteEntityCommand.modelAzName();
        }
        if (command instanceof DeleteAttributeCommand deleteAttributeCommand) {
            return deleteAttributeCommand.modelAzName();
        }
        if (command instanceof DeleteValueSetCommand deleteValueSetCommand) {
            return deleteValueSetCommand.modelAzName();
        }
        if (command instanceof ClearAttributeValueSetCommand clearAttributeValueSetCommand) {
            return clearAttributeValueSetCommand.modelAzName();
        }
        if (command instanceof NoOpCommand) {
            return null;
        }
        throw new IllegalArgumentException("command does not target a model: " + command.getClass().getSimpleName());
    }
}
