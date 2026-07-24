package org.vedenemo.console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ConsoleSession {

    private final UUID backendSessionId;
    private final ModelClient modelClient;
    private final SessionClient sessionClient;
    private final CommandClient commandClient;
    private final ConsoleCapabilities capabilities;
    private String attachedModelAzName;
    private String attachedEntityAzName;
    private List<ModelSummary> latestModels = List.of();
    private List<EntitySummary> latestEntities = List.of();
    private List<AttributeSummary> latestAttributes = List.of();
    private List<AssociationSummary> latestAssociations = List.of();

    public ConsoleSession(
            UUID backendSessionId,
            ModelClient modelClient,
            SessionClient sessionClient,
            CommandClient commandClient,
            ConsoleCapabilities capabilities
    ) {
        this.backendSessionId = Objects.requireNonNull(backendSessionId, "backendSessionId must not be null");
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
        this.commandClient = Objects.requireNonNull(commandClient, "commandClient must not be null");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
    }

    public UUID backendSessionId() {
        return backendSessionId;
    }

    public Optional<String> attachedModelAzName() {
        return Optional.ofNullable(attachedModelAzName);
    }

    public Optional<String> attachedEntityAzName() {
        return Optional.ofNullable(attachedEntityAzName);
    }

    public List<ModelSummary> latestModels() {
        return List.copyOf(latestModels);
    }

    public List<EntitySummary> latestEntities() {
        return List.copyOf(latestEntities);
    }

    public List<AttributeSummary> latestAttributes() {
        return List.copyOf(latestAttributes);
    }

    public List<AssociationSummary> latestAssociations() {
        return List.copyOf(latestAssociations);
    }

    public void refreshModels() throws IOException, InterruptedException {
        latestModels = modelClient.listModels();
    }

    public void attachInitialModel(String modelAzName) throws IOException, InterruptedException {
        if (modelAzName == null || modelAzName.isBlank()) {
            return;
        }
        Optional<ModelSummary> model = resolveModel(modelAzName.trim(), new ArrayList<>());
        if (model.isEmpty()) {
            throw new IOException("model not found");
        }
        attachResolvedModel(model.orElseThrow());
    }

    public ConsoleCommandResult execute(String line) {
        ArrayList<String> output = new ArrayList<>();
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return ConsoleCommandResult.ok(List.of());
        }
        String command = commandName(trimmed).toLowerCase();
        try {
            if ("help".equals(command) && commandOnly(trimmed)) {
                printHelp(output);
            } else if ("ping".equals(command) && commandOnly(trimmed)) {
                ping(output);
            } else if ("list".equals(command) && commandOnly(trimmed)) {
                listModels(output);
            } else if ("entities".equals(command) && commandOnly(trimmed)) {
                listEntities(output);
            } else if ("attributes".equals(command) && commandOnly(trimmed)) {
                listAttributes(output);
            } else if ("associations".equals(command) && commandOnly(trimmed)) {
                listAssociations(output);
            } else if ("detach".equals(command) && commandOnly(trimmed)) {
                detachModel(output);
            } else if ("attach".equals(command)) {
                attachModel(trimmed, output);
            } else if ("entity".equals(command)) {
                handleEntityCommand(trimmed, output);
            } else if ("undo".equals(command) && commandOnly(trimmed)) {
                undo(output);
            } else if ("save".equals(command)) {
                unsupportedFileCommand("save", output);
            } else if ("load".equals(command)) {
                unsupportedFileCommand("load", output);
            } else if ("add".equals(command) || "attr".equals(command) || "assoc".equals(command)) {
                output.add("Command '" + command + "' requires interactive terminal prompts and is not supported in the web console yet.");
                return ConsoleCommandResult.error(output);
            } else {
                output.add("Unknown command: " + trimmed);
                return ConsoleCommandResult.error(output);
            }
            return ConsoleCommandResult.ok(output);
        } catch (IOException exception) {
            output.add(exception.getMessage());
            return ConsoleCommandResult.error(output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            output.add("Command interrupted.");
            return ConsoleCommandResult.error(output);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.add(exception.getMessage());
            return ConsoleCommandResult.error(output);
        }
    }

    public String prompt() {
        if (attachedModelAzName == null) {
            return "VedenemoCli>";
        }
        if (attachedEntityAzName == null) {
            return "VedenemoCli[" + attachedModelAzName + "]>";
        }
        return "VedenemoCli[" + attachedModelAzName + "/" + attachedEntityAzName + "]>";
    }

    private void printHelp(List<String> output) {
        output.add("Available commands:");
        output.add("  ping - check backend connectivity");
        output.add("  list - list existing models");
        output.add("  attach [N | azName] - attach to a listed model");
        output.add("  detach - detach from the current model");
        output.add("  entities - list entities in the attached model");
        output.add("  entity [N | azName] - select an entity in the attached model");
        output.add("  entity detach - clear the selected entity");
        output.add("  attributes - list attributes in the selected entity");
        output.add("  associations - list model associations, or selected entity associations");
        output.add("  undo - undo the latest backend command");
        output.add("  save [N | azName] [outputPath] - not supported in the web console");
        output.add("  load <path> - not supported in the web console");
        output.add("  help - show this help");
    }

    private void ping(List<String> output) throws IOException, InterruptedException {
        modelClient.ping();
        output.add("Backend responded OK.");
    }

    private void listModels(List<String> output) throws IOException, InterruptedException {
        latestModels = modelClient.listModels();
        if (latestModels.isEmpty()) {
            output.add("No models available.");
            return;
        }
        for (int index = 0; index < latestModels.size(); index++) {
            ModelSummary model = latestModels.get(index);
            output.add((index + 1) + ". " + model.visName() + " (" + model.azName() + ") version " + model.version());
        }
    }

    private void attachModel(String line, List<String> output) throws IOException, InterruptedException {
        String argument = line.length() == "attach".length() ? "" : line.substring("attach".length()).trim();
        if (argument.isEmpty()) {
            output.add("Usage: attach [N | azName]");
            return;
        }
        Optional<ModelSummary> model = resolveModel(argument, output);
        if (model.isEmpty()) {
            return;
        }
        attachResolvedModel(model.orElseThrow());
        output.add("Attached to model " + model.orElseThrow().azName() + ".");
    }

    private Optional<ModelSummary> resolveModel(String argument, List<String> output) throws IOException, InterruptedException {
        if (isPositiveInteger(argument)) {
            if (latestModels.isEmpty()) {
                output.add("Run list first before attaching by number.");
                return Optional.empty();
            }
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestModels.size()) {
                output.add("No model found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestModels.get(index));
        }
        List<ModelSummary> models = modelClient.listModels();
        return models.stream()
                .filter(model -> model.azName().equals(argument))
                .findFirst()
                .or(() -> {
                    output.add("No model found with azName " + argument + ".");
                    return Optional.empty();
                });
    }

    private void attachResolvedModel(ModelSummary model) throws IOException, InterruptedException {
        sessionClient.selectModel(backendSessionId, model.azName());
        attachedModelAzName = model.azName();
        attachedEntityAzName = null;
        latestEntities = List.of();
        latestAttributes = List.of();
        latestAssociations = List.of();
    }

    private void detachModel(List<String> output) throws IOException, InterruptedException {
        if (attachedModelAzName == null) {
            output.add("No model is currently attached.");
            return;
        }
        sessionClient.clearSelectedModel(backendSessionId);
        attachedModelAzName = null;
        attachedEntityAzName = null;
        latestEntities = List.of();
        latestAttributes = List.of();
        latestAssociations = List.of();
        output.add("Detached from model.");
    }

    private void listEntities(List<String> output) throws IOException, InterruptedException {
        if (attachedModelAzName == null) {
            output.add("Attach a model before listing entities.");
            return;
        }
        latestEntities = modelClient.listEntities(attachedModelAzName);
        if (latestEntities.isEmpty()) {
            output.add("No entities available.");
            return;
        }
        for (int index = 0; index < latestEntities.size(); index++) {
            EntitySummary entity = latestEntities.get(index);
            output.add((index + 1) + ". " + entity.visName() + " (" + entity.azName() + ") active since " + entity.activeSince());
        }
    }

    private void handleEntityCommand(String line, List<String> output) throws IOException, InterruptedException {
        if ("detach".equals(argumentText(line, "entity").toLowerCase())) {
            detachEntity(output);
            return;
        }
        selectEntity(line, output);
    }

    private void selectEntity(String line, List<String> output) throws IOException, InterruptedException {
        if (attachedModelAzName == null) {
            output.add("Attach a model before selecting an entity.");
            return;
        }
        String argument = line.length() == "entity".length() ? "" : line.substring("entity".length()).trim();
        if (argument.isEmpty()) {
            output.add("Usage: entity [N | azName]");
            return;
        }
        Optional<EntitySummary> entity = resolveEntity(argument, output);
        if (entity.isEmpty()) {
            return;
        }
        attachedEntityAzName = entity.orElseThrow().azName();
        latestAttributes = List.of();
        latestAssociations = List.of();
        output.add("Selected entity " + entity.orElseThrow().azName() + ".");
    }

    private Optional<EntitySummary> resolveEntity(String argument, List<String> output) throws IOException, InterruptedException {
        if (isPositiveInteger(argument)) {
            if (latestEntities.isEmpty()) {
                output.add("Run entities first before selecting by number.");
                return Optional.empty();
            }
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestEntities.size()) {
                output.add("No entity found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestEntities.get(index));
        }
        List<EntitySummary> entities = modelClient.listEntities(attachedModelAzName);
        return entities.stream()
                .filter(entity -> entity.azName().equals(argument))
                .findFirst()
                .or(() -> {
                    output.add("No entity found with azName " + argument + ".");
                    return Optional.empty();
                });
    }

    private void detachEntity(List<String> output) {
        if (attachedEntityAzName == null) {
            output.add("No entity is currently selected.");
            return;
        }
        attachedEntityAzName = null;
        latestAttributes = List.of();
        latestAssociations = List.of();
        output.add("Entity detached.");
    }

    private void listAttributes(List<String> output) throws IOException, InterruptedException {
        if (attachedModelAzName == null) {
            output.add("Attach a model before listing attributes.");
            return;
        }
        if (attachedEntityAzName == null) {
            output.add("Select an entity before listing attributes.");
            return;
        }
        latestAttributes = modelClient.listAttributes(attachedModelAzName, attachedEntityAzName);
        if (latestAttributes.isEmpty()) {
            output.add("No attributes available.");
            return;
        }
        for (int index = 0; index < latestAttributes.size(); index++) {
            AttributeSummary attribute = latestAttributes.get(index);
            output.add((index + 1) + ". "
                    + attribute.visName()
                    + " ("
                    + attribute.azName()
                    + ") type "
                    + attribute.dataType()
                    + " active since "
                    + attribute.activeSince()
                    + deprecatedSuffix(attribute.deprecatedSince()));
        }
    }

    private void listAssociations(List<String> output) throws IOException, InterruptedException {
        if (attachedModelAzName == null) {
            output.add("Attach a model before listing associations.");
            return;
        }
        if (attachedEntityAzName == null) {
            latestAssociations = modelClient.listAssociations(attachedModelAzName);
            output.add("Associations for model " + attachedModelAzName + ":");
        } else {
            latestAssociations = modelClient.listAssociations(attachedModelAzName, attachedEntityAzName);
            output.add("Associations for entity " + attachedEntityAzName + " in model " + attachedModelAzName + ":");
        }
        if (latestAssociations.isEmpty()) {
            output.add("No associations available.");
            return;
        }
        for (int index = 0; index < latestAssociations.size(); index++) {
            AssociationSummary association = latestAssociations.get(index);
            output.add((index + 1) + ". "
                    + association.visName()
                    + " ("
                    + association.azName()
                    + ") "
                    + association.kind()
                    + " "
                    + association.sourceEntityAzName()
                    + " -> "
                    + association.targetEntityAzName()
                    + " ["
                    + association.cardinality()
                    + "]"
                    + relationEndSuffix(association)
                    + " active since "
                    + association.activeSince()
                    + deprecatedSuffix(association.deprecatedSince()));
        }
    }

    private void undo(List<String> output) throws IOException, InterruptedException {
        UndoCommandResult result = commandClient.undo(backendSessionId);
        if (result.isNothingToUndo()) {
            output.add("Nothing to undo.");
        } else {
            output.add(undoMessage(result));
        }
    }

    private void unsupportedFileCommand(String command, List<String> output) {
        if (capabilities.localFileAccess()) {
            output.add("Command '" + command + "' is handled by the terminal CLI.");
            return;
        }
        output.add("Command '" + command + "' is not supported in the web console because it requires local file access.");
    }

    private static String commandName(String value) {
        int spaceIndex = value.indexOf(' ');
        return spaceIndex < 0 ? value : value.substring(0, spaceIndex);
    }

    private static boolean commandOnly(String value) {
        return value.indexOf(' ') < 0;
    }

    private static String argumentText(String line, String command) {
        return line.length() == command.length() ? "" : line.substring(command.length()).trim();
    }

    private static boolean isPositiveInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String deprecatedSuffix(String deprecatedSince) {
        if (deprecatedSince == null) {
            return "";
        }
        return " deprecated since " + deprecatedSince;
    }

    private static String relationEndSuffix(AssociationSummary association) {
        if (!"RELATION".equals(association.kind())) {
            return "";
        }
        return " roles "
                + association.sourceRoleName()
                + "["
                + association.sourceCardinality()
                + "] <-> "
                + association.targetRoleName()
                + "["
                + association.targetCardinality()
                + "]";
    }

    private static String undoMessage(UndoCommandResult result) {
        if ("create-attribute".equals(result.undoneCommand())) {
            return "Undo completed: removed attribute "
                    + result.attributeAzName()
                    + " from entity "
                    + result.entityAzName()
                    + " in model "
                    + result.modelAzName()
                    + ".";
        }
        if ("create-association".equals(result.undoneCommand())) {
            return "Undo completed: removed association "
                    + result.associationAzName()
                    + " from model "
                    + result.modelAzName()
                    + ".";
        }
        if ("create-entity".equals(result.undoneCommand())) {
            return "Undo completed: removed entity "
                    + result.entityAzName()
                    + " from model "
                    + result.modelAzName()
                    + ".";
        }
        return "Undo completed: " + result.undoneCommand() + " in model " + result.modelAzName() + ".";
    }
}
