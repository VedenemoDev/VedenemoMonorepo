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
    private List<SnapshotSummary> latestSnapshots = List.of();
    private PromptFlow promptFlow;

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
        if (line != null && line.indexOf('\u001b') >= 0) {
            promptFlow = null;
            return ConsoleCommandResult.ok(List.of("Operation cancelled."));
        }
        if (promptFlow != null) {
            try {
                ConsoleCommandResult result = promptFlow.accept(trimmed);
                if (promptFlow.isComplete()) {
                    promptFlow = null;
                }
                return result;
            } catch (IOException exception) {
                promptFlow = null;
                output.add(exception.getMessage());
                return ConsoleCommandResult.error(output);
            } catch (InterruptedException exception) {
                promptFlow = null;
                Thread.currentThread().interrupt();
                output.add("Command interrupted.");
                return ConsoleCommandResult.error(output);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                promptFlow = null;
                output.add(exception.getMessage());
                return ConsoleCommandResult.error(output);
            }
        }
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
            } else if ("add".equals(command) && commandOnly(trimmed)) {
                startAddFlow();
            } else if ("attr".equals(command)) {
                startAttributeFlow(trimmed, output);
            } else if ("assoc".equals(command)) {
                startAssociationFlow(trimmed, output);
            } else if ("save".equals(command)) {
                saveSnapshot(trimmed, output);
            } else if ("load".equals(command)) {
                loadSnapshot(trimmed, output);
            } else if ("snapshots".equals(command)) {
                listSnapshots(trimmed, output);
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
        if (promptFlow != null) {
            return promptFlow.prompt();
        }
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
        output.add("  add - add a new model or entity in the attached model");
        output.add("  attach [N | azName] - attach to a listed model");
        output.add("  detach - detach from the current model");
        output.add("  entities - list entities in the attached model");
        output.add("  entity [N | azName] - select an entity in the attached model");
        output.add("  entity detach - clear the selected entity");
        output.add("  attributes - list attributes in the selected entity");
        output.add("  attr add - add an attribute to the selected entity");
        output.add("  associations - list model associations, or selected entity associations");
        output.add("  assoc add [ownership | reference | relation] - add an association or relation");
        output.add("  undo - undo the latest backend command");
        if (capabilities.cloudSnapshots()) {
            output.add("  save [snapshotName] - save the attached model to a cloud snapshot");
            output.add("  snapshots - list cloud snapshots");
            output.add("  load <snapshot-key | snapshot-number> - load a model from a cloud snapshot");
        } else {
            output.add("  save [N | azName] [outputPath] - not supported in the web console");
            output.add("  snapshots - not supported in the web console");
            output.add("  load <path | snapshot-number> - not supported in the web console");
        }
        output.add("  help - show this help");
        output.add("  Esc - cancel the current interactive prompt");
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

    private void startAddFlow() {
        promptFlow = attachedModelAzName == null ? new AddModelFlow() : new AddEntityFlow();
    }

    private void startAttributeFlow(String line, List<String> output) {
        if (!"add".equals(argumentText(line, "attr").toLowerCase())) {
            output.add("Unknown command: " + line);
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before adding an attribute.");
            return;
        }
        if (attachedEntityAzName == null) {
            output.add("Select an entity before adding an attribute.");
            return;
        }
        promptFlow = new AddAttributeFlow();
    }

    private void startAssociationFlow(String line, List<String> output) {
        List<String> arguments = splitArguments(argumentText(line, "assoc"));
        if (arguments.isEmpty() || !"add".equalsIgnoreCase(arguments.getFirst())) {
            output.add("Usage: assoc add [ownership | reference | relation]");
            return;
        }
        if (arguments.size() > 2) {
            output.add("Usage: assoc add [ownership | reference | relation]");
            return;
        }
        String kind = arguments.size() == 2 ? normalizeAssociationKind(arguments.get(1)) : null;
        if (kind == null && arguments.size() == 2) {
            output.add("Association kind is required.");
            return;
        }
        promptFlow = kind == null ? new AssociationKindFlow() : associationFlow(kind);
    }

    private PromptFlow associationFlow(String kind) {
        if ("relation".equals(kind)) {
            return new AddRelationFlow();
        }
        return new AddAssociationFlow(kind);
    }

    private void unsupportedFileCommand(String command, List<String> output) {
        if (capabilities.localFileAccess()) {
            output.add("Command '" + command + "' is handled by the terminal CLI.");
            return;
        }
        output.add("Command '" + command + "' is not supported in the web console because it requires local file access.");
    }

    private void saveSnapshot(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("save", output);
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before saving a snapshot.");
            return;
        }
        List<String> arguments = splitArguments(argumentText(line, "save"));
        if (arguments.size() > 1) {
            output.add("Usage: save [snapshotName]");
            return;
        }
        if (arguments.isEmpty()) {
            promptFlow = new SaveSnapshotFlow();
            return;
        }
        saveSnapshotToCloud(arguments.getFirst(), output);
    }

    private void saveSnapshotToCloud(String snapshotName, List<String> output) throws IOException, InterruptedException {
        SnapshotSummary snapshot = modelClient.saveSnapshot(attachedModelAzName, snapshotName);
        latestSnapshots = modelClient.listSnapshots();
        output.add("Saved model " + attachedModelAzName + " to cloud snapshot " + snapshot.key() + ".");
    }

    private void listSnapshots(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("snapshots", output);
            return;
        }
        if (!commandOnly(line)) {
            output.add("Usage: snapshots");
            return;
        }
        latestSnapshots = modelClient.listSnapshots();
        if (latestSnapshots.isEmpty()) {
            output.add("No cloud snapshots available.");
            return;
        }
        output.add("Cloud snapshots:");
        for (int index = 0; index < latestSnapshots.size(); index++) {
            SnapshotSummary snapshot = latestSnapshots.get(index);
            output.add((index + 1) + ". "
                    + snapshot.key()
                    + " - "
                    + snapshot.modelVisName()
                    + " ("
                    + snapshot.modelAzName()
                    + ") version "
                    + snapshot.modelVersion()
                    + ", "
                    + snapshot.commandCount()
                    + " commands, saved "
                    + snapshot.savedAt());
        }
    }

    private void loadSnapshot(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("load", output);
            return;
        }
        String argument = argumentText(line, "load");
        if (argument.isBlank()) {
            output.add("Usage: load <snapshot-key | snapshot-number>");
            return;
        }
        String snapshotKey = resolveSnapshotKey(argument, output);
        if (snapshotKey == null) {
            return;
        }
        importSnapshotWithRenamePrompt(snapshotKey, null, output);
    }

    private String resolveSnapshotKey(String argument, List<String> output) {
        String value = argument.trim();
        if (!isPositiveInteger(value)) {
            return value;
        }
        if (latestSnapshots.isEmpty()) {
            output.add("Run snapshots first before loading by number.");
            return null;
        }
        int index = Integer.parseInt(value) - 1;
        if (index < 0 || index >= latestSnapshots.size()) {
            output.add("No snapshot found for list number " + value + ".");
            return null;
        }
        return latestSnapshots.get(index).key();
    }

    private boolean importSnapshotWithRenamePrompt(String snapshotKey, String modelAzNameOverride, List<String> output)
            throws IOException, InterruptedException {
        try {
            ModelImportResult result = modelClient.loadSnapshot(snapshotKey, modelAzNameOverride);
            refreshModels();
            Optional<ModelSummary> importedModel = resolveModel(result.modelAzName(), output);
            if (importedModel.isPresent()) {
                attachResolvedModel(importedModel.orElseThrow());
            }
            output.add("Loaded model " + result.modelAzName() + " from cloud snapshot " + snapshotKey + ".");
            return true;
        } catch (ModelAlreadyExistsException exception) {
            output.add(exception.getMessage());
            promptFlow = new LoadSnapshotRenameFlow(snapshotKey);
            return false;
        }
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

    private static List<String> splitArguments(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim().split("\\s+"));
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

    private static String normalizeAssociationKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "ownership" -> "ownership";
            case "2", "reference" -> "reference";
            case "3", "relation" -> "relation";
            default -> null;
        };
    }

    private static String normalizeDataTypeInput(String value) {
        if (value == null || value.isBlank()) {
            return "TEXT";
        }
        return switch (value.trim().toLowerCase()) {
            case "text" -> "TEXT";
            case "numeric", "number" -> "NUMERIC";
            case "url" -> "URL";
            case "data" -> "DATA";
            case "date" -> "DATE";
            case "time" -> "TIME";
            case "datetime" -> "DATETIME";
            default -> value.trim();
        };
    }

    private static String suggestAzName(String visName) {
        StringBuilder suggestion = new StringBuilder();
        boolean previousWasSeparator = true;
        for (int index = 0; index < visName.length(); index++) {
            char character = visName.charAt(index);
            if (isAsciiLetter(character)) {
                suggestion.append(character);
                previousWasSeparator = false;
            } else if (isAsciiDigit(character) && !suggestion.isEmpty()) {
                suggestion.append(character);
                previousWasSeparator = false;
            } else if (!previousWasSeparator && suggestion.length() > 0) {
                suggestion.append('_');
                previousWasSeparator = true;
            }
        }
        while (!suggestion.isEmpty() && suggestion.charAt(suggestion.length() - 1) == '_') {
            suggestion.deleteCharAt(suggestion.length() - 1);
        }
        if (suggestion.isEmpty()) {
            return null;
        }
        return suggestion.toString();
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static String cleanAssociationSuggestion(String value) {
        String suggestion = suggestAzName(value);
        if (suggestion == null || suggestion.isBlank()) {
            return null;
        }
        return suggestion;
    }

    private boolean isAssociationAzNameAvailable(String azName) throws IOException, InterruptedException {
        return modelClient.listAssociations(attachedModelAzName).stream()
                .noneMatch(association -> association.azName().equalsIgnoreCase(azName));
    }

    private String suggestAssociationAzName(String kind, String source, String target, String visName) throws IOException, InterruptedException {
        String base = source + "_" + Objects.requireNonNullElse(suggestAzName(visName), "");
        String suggestion = cleanAssociationSuggestion(base);
        if (suggestion == null) {
            suggestion = cleanAssociationSuggestion(source + "_" + target);
        }
        if (suggestion == null) {
            suggestion = "Association";
        }
        if (isAssociationAzNameAvailable(suggestion)) {
            return suggestion;
        }
        String withTarget = cleanAssociationSuggestion(source + "_" + target);
        if (withTarget != null && isAssociationAzNameAvailable(withTarget)) {
            return withTarget;
        }
        String withKind = cleanAssociationSuggestion(source + "_" + kind + "_" + target);
        if (withKind != null && isAssociationAzNameAvailable(withKind)) {
            return withKind;
        }
        int suffix = 2;
        while (!isAssociationAzNameAvailable(suggestion + "_" + suffix)) {
            suffix++;
        }
        return suggestion + "_" + suffix;
    }

    private String resolveEntityReference(String value, List<String> output) throws IOException, InterruptedException {
        if (value == null || value.trim().isEmpty()) {
            output.add("Entity identifier is required.");
            return null;
        }
        String trimmed = value.trim();
        if (!isPositiveInteger(trimmed)) {
            return trimmed;
        }
        if (latestEntities.isEmpty()) {
            output.add("Run entities first before selecting an entity by number.");
            return null;
        }
        int index = Integer.parseInt(trimmed) - 1;
        if (index < 0 || index >= latestEntities.size()) {
            output.add("No entity found for list number " + trimmed + ".");
            return null;
        }
        return latestEntities.get(index).azName();
    }

    private interface PromptFlow {
        String prompt();

        ConsoleCommandResult accept(String input) throws IOException, InterruptedException;

        boolean isComplete();
    }

    private abstract static class BasePromptFlow implements PromptFlow {
        protected int step;
        protected boolean complete;

        @Override
        public boolean isComplete() {
            return complete;
        }
    }

    private final class AddModelFlow extends BasePromptFlow {
        private String visName;
        private String suggestion;

        @Override
        public String prompt() {
            return step == 0 ? "Model visible name: " : "Model azName [" + suggestion + "]: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            ArrayList<String> output = new ArrayList<>();
            if (step == 0) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Model visible name is required."));
                }
                visName = input;
                suggestion = suggestAzName(visName);
                if (suggestion == null) {
                    suggestion = "";
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            String azName = input == null || input.isBlank() ? suggestion : input.trim();
            if (azName.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Model azName is required."));
            }
            ModelSummary created = modelClient.addModel(azName, visName, "1.0.0");
            attachResolvedModel(created);
            refreshModels();
            output.add("Attached to model " + created.azName() + ".");
            output.add("Added and attached model " + created.azName() + ".");
            complete = true;
            return ConsoleCommandResult.ok(output);
        }
    }

    private final class SaveSnapshotFlow extends BasePromptFlow {
        @Override
        public String prompt() {
            return "Snapshot name: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            if (input == null || input.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Snapshot name is required."));
            }
            ArrayList<String> output = new ArrayList<>();
            saveSnapshotToCloud(input.trim(), output);
            complete = true;
            return ConsoleCommandResult.ok(output);
        }
    }

    private final class LoadSnapshotRenameFlow extends BasePromptFlow {
        private final String snapshotKey;

        private LoadSnapshotRenameFlow(String snapshotKey) {
            this.snapshotKey = snapshotKey;
        }

        @Override
        public String prompt() {
            return "New model azName for import, or blank to cancel: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            if (input == null || input.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Load cancelled."));
            }
            ArrayList<String> output = new ArrayList<>();
            complete = importSnapshotWithRenamePrompt(snapshotKey, input.trim(), output);
            return ConsoleCommandResult.ok(output);
        }
    }

    private final class AddEntityFlow extends BasePromptFlow {
        private String visName;
        private String suggestion;

        @Override
        public String prompt() {
            return step == 0 ? "Entity visible name: " : "Entity azName [" + suggestion + "]: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            if (step == 0) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Entity visible name is required."));
                }
                visName = input;
                suggestion = suggestAzName(visName);
                if (suggestion == null) {
                    suggestion = "";
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            String azName = input == null || input.isBlank() ? suggestion : input.trim();
            if (azName.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Entity azName is required."));
            }
            commandClient.createEntity(backendSessionId, azName, visName);
            complete = true;
            return ConsoleCommandResult.ok(List.of("Entity " + azName + " added."));
        }
    }

    private final class AddAttributeFlow extends BasePromptFlow {
        private String visName;
        private String suggestion;
        private String azName;

        @Override
        public String prompt() {
            return switch (step) {
                case 0 -> "Attribute visible name: ";
                case 1 -> "Attribute azName [" + suggestion + "]: ";
                default -> "Attribute data type [TEXT]: ";
            };
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            if (step == 0) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Attribute visible name is required."));
                }
                visName = input;
                suggestion = suggestAzName(visName);
                if (suggestion == null) {
                    suggestion = "";
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 1) {
                azName = input == null || input.isBlank() ? suggestion : input.trim();
                if (azName.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Attribute azName is required."));
                }
                step = 2;
                return ConsoleCommandResult.ok(List.of());
            }
            String dataType = normalizeDataTypeInput(input);
            commandClient.createAttribute(backendSessionId, attachedEntityAzName, azName, visName, dataType);
            complete = true;
            return ConsoleCommandResult.ok(List.of("Attribute " + azName + " added."));
        }
    }

    private final class AssociationKindFlow extends BasePromptFlow {
        @Override
        public String prompt() {
            return "Association kind [1 ownership, 2 reference, 3 relation]: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) {
            String kind = normalizeAssociationKind(input);
            if (kind == null) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Association kind is required."));
            }
            promptFlow = associationFlow(kind);
            complete = false;
            return ConsoleCommandResult.ok(List.of());
        }
    }

    private final class AddAssociationFlow extends BasePromptFlow {
        private final String kind;
        private String source;
        private String target;
        private String visName;
        private String cardinality;
        private String suggestion;

        private AddAssociationFlow(String kind) {
            this.kind = kind;
        }

        @Override
        public String prompt() {
            return switch (step) {
                case 0 -> "Source entity number or azName: ";
                case 1 -> "Target entity number or azName: ";
                case 2 -> "Association visible name: ";
                case 3 -> "Association cardinality [1]: ";
                default -> "Association azName [" + suggestion + "]: ";
            };
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            ArrayList<String> output = new ArrayList<>();
            if (attachedModelAzName == null) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Attach a model before adding an association."));
            }
            if (step == 0) {
                source = resolveEntityReference(input, output);
                if (source == null) {
                    complete = true;
                    return ConsoleCommandResult.ok(output);
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 1) {
                target = resolveEntityReference(input, output);
                if (target == null) {
                    complete = true;
                    return ConsoleCommandResult.ok(output);
                }
                step = 2;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 2) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Association visible name is required."));
                }
                visName = input;
                step = 3;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 3) {
                cardinality = input == null || input.isBlank() ? "1" : input.trim();
                suggestion = suggestAssociationAzName(kind, source, target, visName);
                step = 4;
                return ConsoleCommandResult.ok(List.of());
            }
            String azName = input == null || input.isBlank() ? suggestion : input.trim();
            if (azName.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Association azName is required."));
            }
            commandClient.createAssociation(
                    backendSessionId,
                    kind,
                    azName,
                    visName,
                    source,
                    target,
                    cardinality,
                    null,
                    null,
                    null,
                    null
            );
            complete = true;
            return ConsoleCommandResult.ok(List.of("Association " + azName + " added."));
        }
    }

    private final class AddRelationFlow extends BasePromptFlow {
        private String source;
        private String sourceRole;
        private String sourceCardinality;
        private String target;
        private String targetRole;
        private String targetCardinality;
        private String visName;
        private String suggestion;

        @Override
        public String prompt() {
            return switch (step) {
                case 0 -> "First end entity number or azName: ";
                case 1 -> "First end role name: ";
                case 2 -> "First end cardinality [1]: ";
                case 3 -> "Second end entity number or azName: ";
                case 4 -> "Second end role name: ";
                case 5 -> "Second end cardinality [1]: ";
                case 6 -> "Relation visible name: ";
                default -> "Relation azName [" + suggestion + "]: ";
            };
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            ArrayList<String> output = new ArrayList<>();
            if (attachedModelAzName == null) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Attach a model before adding a relation."));
            }
            if (step == 0) {
                source = resolveEntityReference(input, output);
                if (source == null) {
                    complete = true;
                    return ConsoleCommandResult.ok(output);
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 1) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("First end role name is required."));
                }
                sourceRole = input.trim();
                step = 2;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 2) {
                sourceCardinality = input == null || input.isBlank() ? "1" : input.trim();
                step = 3;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 3) {
                target = resolveEntityReference(input, output);
                if (target == null) {
                    complete = true;
                    return ConsoleCommandResult.ok(output);
                }
                step = 4;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 4) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Second end role name is required."));
                }
                targetRole = input.trim();
                step = 5;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 5) {
                targetCardinality = input == null || input.isBlank() ? "1" : input.trim();
                step = 6;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 6) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("Relation visible name is required."));
                }
                visName = input;
                suggestion = suggestAssociationAzName("relation", source, target, visName);
                step = 7;
                return ConsoleCommandResult.ok(List.of());
            }
            String azName = input == null || input.isBlank() ? suggestion : input.trim();
            if (azName.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("Relation azName is required."));
            }
            commandClient.createAssociation(
                    backendSessionId,
                    "relation",
                    azName,
                    visName,
                    source,
                    target,
                    targetCardinality,
                    sourceRole,
                    targetRole,
                    sourceCardinality,
                    targetCardinality
            );
            complete = true;
            return ConsoleCommandResult.ok(List.of("Relation " + azName + " added."));
        }
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
