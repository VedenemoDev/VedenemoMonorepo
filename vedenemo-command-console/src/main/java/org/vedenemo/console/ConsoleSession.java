package org.vedenemo.console;

import java.io.IOException;
import java.time.LocalDate;
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
    private List<ModelInstanceRootSummary> latestInstanceRoots = List.of();
    private List<DumpSummary> latestDumps = List.of();
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
            } else if ("vset".equals(command)) {
                startValueSetFlow(trimmed, output);
            } else if ("assoc".equals(command)) {
                startAssociationFlow(trimmed, output);
            } else if ("msave".equals(command)) {
                saveSnapshot(trimmed, output);
            } else if ("mload".equals(command)) {
                loadSnapshot(trimmed, output);
            } else if ("snapshots".equals(command)) {
                listSnapshots(trimmed, output);
            } else if ("roots".equals(command)) {
                listInstanceRoots(trimmed, output);
            } else if ("dumps".equals(command)) {
                listDumps(trimmed, output);
            } else if ("dsave".equals(command)) {
                saveDump(trimmed, output);
            } else if ("dload".equals(command)) {
                loadDump(trimmed, output);
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
        output.add("  attr vset - attach a ValueSet to an attribute in the selected entity");
        output.add("  vset add - add a model-level ValueSet");
        output.add("  associations - list model associations, or selected entity associations");
        output.add("  assoc add [ownership | reference | relation] - add an association or relation");
        output.add("  undo - undo the latest backend command");
        if (capabilities.cloudSnapshots()) {
            output.add("  msave [snapshotName] - save the attached model to a cloud snapshot");
            output.add("  snapshots - list cloud snapshots");
            output.add("  mload <snapshot-key | snapshot-number> - load a model from a cloud snapshot");
            output.add("  roots - list model-instance roots for the attached model");
            output.add("  dumps - list cloud model-instance data dumps");
            output.add("  dsave [root-id | root-number | root-name] [dumpName] - save a model-instance root to a cloud dump");
            output.add("  dload <dump-key | dump-number> - load a cloud dump into a new model-instance root");
        } else {
            output.add("  msave [N | azName] [outputPath] - not supported in the web console");
            output.add("  snapshots - not supported in the web console");
            output.add("  mload <path | snapshot-number> - not supported in the web console");
            output.add("  roots - list model-instance roots for the attached model");
            output.add("  dumps - not supported in the web console");
            output.add("  dsave [root-id | root-number | root-name] [outputPath] - not supported in the web console");
            output.add("  dload <path | dump-number> - not supported in the web console");
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
        latestInstanceRoots = List.of();
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
        latestInstanceRoots = List.of();
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
            output.add((index + 1) + ". "
                    + entity.visName()
                    + " ("
                    + entity.azName()
                    + ") active since "
                    + entity.activeSince()
                    + deprecatedSuffix(entity.deprecatedSince())
                    + retiredSuffix(entity.retiredSince()));
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
                    + valueSetSuffix(attribute.valueSetAzName())
                    + " active since "
                    + attribute.activeSince()
                    + deprecatedSuffix(attribute.deprecatedSince())
                    + retiredSuffix(attribute.retiredSince()));
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
                    + deprecatedSuffix(association.deprecatedSince())
                    + retiredSuffix(association.retiredSince()));
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
        String argument = argumentText(line, "attr").toLowerCase();
        if (!"add".equals(argument) && !"vset".equals(argument) && !"valueset".equals(argument)) {
            output.add("Usage: attr add | attr vset");
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before changing attributes.");
            return;
        }
        if (attachedEntityAzName == null) {
            output.add("Select an entity before changing attributes.");
            return;
        }
        promptFlow = "add".equals(argument) ? new AddAttributeFlow() : new AttachAttributeValueSetFlow();
    }

    private void startValueSetFlow(String line, List<String> output) {
        if (!"add".equals(argumentText(line, "vset").toLowerCase())) {
            output.add("Usage: vset add");
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before adding a ValueSet.");
            return;
        }
        promptFlow = new AddValueSetFlow();
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
            unsupportedFileCommand("msave", output);
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before saving a snapshot.");
            return;
        }
        List<String> arguments = splitArguments(argumentText(line, "msave"));
        if (arguments.size() > 1) {
            output.add("Usage: msave [snapshotName]");
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
            unsupportedFileCommand("mload", output);
            return;
        }
        String argument = argumentText(line, "mload");
        if (argument.isBlank()) {
            output.add("Usage: mload <snapshot-key | snapshot-number>");
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

    private void listDumps(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("dumps", output);
            return;
        }
        if (!commandOnly(line)) {
            output.add("Usage: dumps");
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before listing dumps.");
            return;
        }
        latestDumps = modelClient.listDumps(attachedModelAzName);
        if (latestDumps.isEmpty()) {
            output.add("No cloud dumps available.");
            return;
        }
        output.add("Cloud dumps:");
        for (int index = 0; index < latestDumps.size(); index++) {
            DumpSummary dump = latestDumps.get(index);
            output.add((index + 1) + ". "
                    + dump.key()
                    + " - "
                    + nullText(dump.rootVisName())
                    + " for "
                    + dump.modelVisName()
                    + " ("
                    + dump.modelAzName()
                    + ") version "
                    + dump.modelVersion()
                    + ", "
                    + dump.entityRecordCount()
                    + " records, "
                    + dump.associationLinkCount()
                    + " links, saved "
                    + dump.savedAt());
        }
    }

    private void listInstanceRoots(String line, List<String> output) throws IOException, InterruptedException {
        if (!commandOnly(line)) {
            output.add("Usage: roots");
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before listing model-instance roots.");
            return;
        }
        latestInstanceRoots = modelClient.listInstanceRoots(attachedModelAzName);
        if (latestInstanceRoots.isEmpty()) {
            output.add("No model-instance roots available for model " + attachedModelAzName + ".");
            return;
        }
        output.add("Model-instance roots for model " + attachedModelAzName + ":");
        for (int index = 0; index < latestInstanceRoots.size(); index++) {
            output.add(formatRoot(index, latestInstanceRoots.get(index)));
        }
    }

    private void saveDump(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("dsave", output);
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before saving a data dump.");
            return;
        }
        List<String> arguments = splitArguments(argumentText(line, "dsave"));
        if (arguments.size() > 2) {
            output.add("Usage: dsave [root-id | root-number | root-name] [dumpName]");
            return;
        }
        if (latestInstanceRoots.isEmpty()) {
            latestInstanceRoots = modelClient.listInstanceRoots(attachedModelAzName);
        }
        Optional<ModelInstanceRootSummary> root = resolveDumpRoot(arguments.isEmpty() ? "" : arguments.getFirst(), output);
        if (root.isEmpty()) {
            return;
        }
        if (arguments.size() < 2) {
            promptFlow = new SaveDumpNameFlow(root.orElseThrow());
            return;
        }
        saveDumpToCloud(root.orElseThrow().instanceRootId(), arguments.get(1), output);
    }

    private void saveDumpToCloud(String instanceRootId, String dumpName, List<String> output) throws IOException, InterruptedException {
        DumpSummary dump = modelClient.saveDump(attachedModelAzName, instanceRootId, dumpName);
        latestDumps = modelClient.listDumps(attachedModelAzName);
        output.add("Saved model-instance root " + instanceRootId + " to cloud dump " + dump.key() + ".");
    }

    private void loadDump(String line, List<String> output) throws IOException, InterruptedException {
        if (!capabilities.cloudSnapshots()) {
            unsupportedFileCommand("dload", output);
            return;
        }
        if (attachedModelAzName == null) {
            output.add("Attach a model before loading a data dump.");
            return;
        }
        String argument = argumentText(line, "dload");
        if (argument.isBlank()) {
            output.add("Usage: dload <dump-key | dump-number>");
            return;
        }
        String dumpKey = resolveDumpKey(argument, output);
        if (dumpKey == null) {
            return;
        }
        DumpPrecheckResult precheck = modelClient.precheckStoredDump(attachedModelAzName, dumpKey);
        writePrecheckMessages(precheck, output);
        if (!precheck.importable()) {
            return;
        }
        if (precheck.confirmationRequired()) {
            promptFlow = new LoadDumpConfirmationFlow(dumpKey);
            return;
        }
        importStoredDump(dumpKey, false, output);
    }

    private Optional<ModelInstanceRootSummary> resolveDumpRoot(String argument, List<String> output) {
        if (latestInstanceRoots.isEmpty()) {
            output.add("No model-instance roots available.");
            return Optional.empty();
        }
        if (argument == null || argument.isBlank()) {
            if (latestInstanceRoots.size() == 1) {
                return Optional.of(latestInstanceRoots.getFirst());
            }
            output.add("Multiple model-instance roots are available. Provide a root number, root id, or root visible name.");
            for (int index = 0; index < latestInstanceRoots.size(); index++) {
                output.add(formatRoot(index, latestInstanceRoots.get(index)));
            }
            return Optional.empty();
        }
        String value = argument.trim();
        if (isPositiveInteger(value)) {
            int index = Integer.parseInt(value) - 1;
            if (index >= 0 && index < latestInstanceRoots.size()) {
                return Optional.of(latestInstanceRoots.get(index));
            }
            output.add("No model-instance root found for list number " + value + ".");
            return Optional.empty();
        }
        return latestInstanceRoots.stream()
                .filter(root -> root.instanceRootId().equals(value) || (root.visName() != null && root.visName().equals(value)))
                .findFirst()
                .or(() -> {
                    output.add("No model-instance root found for " + value + ".");
                    return Optional.empty();
                });
    }

    private static String formatRoot(int index, ModelInstanceRootSummary root) {
        return (index + 1)
                + ". "
                + nullText(root.visName())
                + " version "
                + nullText(root.modelVersion())
                + " ("
                + root.instanceRootId()
                + ")";
    }

    private String resolveDumpKey(String argument, List<String> output) {
        String value = argument.trim();
        if (!isPositiveInteger(value)) {
            return value;
        }
        if (latestDumps.isEmpty()) {
            output.add("Run dumps first before loading by number.");
            return null;
        }
        int index = Integer.parseInt(value) - 1;
        if (index < 0 || index >= latestDumps.size()) {
            output.add("No dump found for list number " + value + ".");
            return null;
        }
        return latestDumps.get(index).key();
    }

    private void importStoredDump(String dumpKey, boolean confirmVersionMismatch, List<String> output) throws IOException, InterruptedException {
        DumpImportResult result = modelClient.loadStoredDump(attachedModelAzName, dumpKey, confirmVersionMismatch);
        output.add("Loaded data dump " + dumpKey + " into model-instance root " + result.root().instanceRootId() + ".");
        writeImportResult(result, output);
    }

    private static void writePrecheckMessages(DumpPrecheckResult precheck, List<String> output) {
        precheck.warnings().forEach(warning -> output.add("Warning: " + warning));
        precheck.diagnostics().forEach(diagnostic -> output.add("Diagnostic: " + diagnostic));
    }

    private static void writeImportResult(DumpImportResult result, List<String> output) {
        result.createdEntityCounts().forEach((entity, count) -> output.add("Created " + count + " " + entity + " records."));
        output.add("Created " + result.createdAssociationLinkCount() + " association links.");
        if (result.skippedDuplicateLinkCount() > 0) {
            output.add("Skipped " + result.skippedDuplicateLinkCount() + " duplicate association links.");
        }
        result.warnings().forEach(warning -> output.add("Warning: " + warning));
        result.failedInserts().forEach(failure -> output.add("Failed insert: " + failure));
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

    private static String nullText(String value) {
        return value == null || value.isBlank() ? "(unnamed)" : value;
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

    private static String retiredSuffix(String retiredSince) {
        if (retiredSince == null) {
            return "";
        }
        return " retired since " + retiredSince;
    }

    private static String valueSetSuffix(String valueSetAzName) {
        if (valueSetAzName == null) {
            return "";
        }
        return " valueSet " + valueSetAzName;
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
            case "location" -> "LOCATION";
            case "location_line", "location-line" -> "LOCATION_LINE";
            case "location_area", "location-area" -> "LOCATION_AREA";
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

    private static String defaultDumpName(String modelAzName, ModelInstanceRootSummary root) {
        String rootPart = root.visName() == null || root.visName().isBlank() ? "root" : root.visName();
        String base = suggestAzName(modelAzName + "_" + rootPart + "_v" + root.modelVersion().replace('.', '_') + "_" + LocalDate.now());
        if (base == null || base.isBlank()) {
            return modelAzName + "_dump";
        }
        return base;
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

    private String resolveAttributeReference(String value, List<String> output) throws IOException, InterruptedException {
        if (value == null || value.trim().isEmpty()) {
            output.add("Attribute identifier is required.");
            return null;
        }
        String trimmed = value.trim();
        if (!isPositiveInteger(trimmed)) {
            return trimmed;
        }
        if (latestAttributes.isEmpty()) {
            latestAttributes = modelClient.listAttributes(attachedModelAzName, attachedEntityAzName);
        }
        int index = Integer.parseInt(trimmed) - 1;
        if (index < 0 || index >= latestAttributes.size()) {
            output.add("No attribute found for list number " + trimmed + ".");
            return null;
        }
        return latestAttributes.get(index).azName();
    }

    private static List<ValueSetEntryInput> parseValueSetEntries(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("At least one ValueSet entry is required.");
        }
        ArrayList<ValueSetEntryInput> entries = new ArrayList<>();
        for (String rawEntry : input.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("ValueSet entries must use TECHNICAL=Visual Name.");
            }
            entries.add(new ValueSetEntryInput(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim()));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("At least one ValueSet entry is required.");
        }
        return List.copyOf(entries);
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

    private final class SaveDumpNameFlow extends BasePromptFlow {
        private final ModelInstanceRootSummary root;
        private final String suggestion;

        private SaveDumpNameFlow(ModelInstanceRootSummary root) {
            this.root = root;
            this.suggestion = defaultDumpName(attachedModelAzName, root);
        }

        @Override
        public String prompt() {
            return "Dump name [" + suggestion + "]: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            String dumpName = input == null || input.isBlank() ? suggestion : input.trim();
            ArrayList<String> output = new ArrayList<>();
            saveDumpToCloud(root.instanceRootId(), dumpName, output);
            complete = true;
            return ConsoleCommandResult.ok(output);
        }
    }

    private final class LoadDumpConfirmationFlow extends BasePromptFlow {
        private final String dumpKey;

        private LoadDumpConfirmationFlow(String dumpKey) {
            this.dumpKey = dumpKey;
        }

        @Override
        public String prompt() {
            return "Load older dump into newer model? [y/N]: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            complete = true;
            if (!"y".equalsIgnoreCase(input == null ? "" : input.trim())) {
                return ConsoleCommandResult.ok(List.of("Dump load cancelled."));
            }
            ArrayList<String> output = new ArrayList<>();
            importStoredDump(dumpKey, true, output);
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

    private final class AddValueSetFlow extends BasePromptFlow {
        private String azName;
        private String dataType;

        @Override
        public String prompt() {
            return switch (step) {
                case 0 -> "ValueSet azName: ";
                case 1 -> "ValueSet data type [TEXT]: ";
                default -> "Entries as TECHNICAL=Visual Name, comma separated: ";
            };
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            if (step == 0) {
                if (input == null || input.isBlank()) {
                    complete = true;
                    return ConsoleCommandResult.ok(List.of("ValueSet azName is required."));
                }
                azName = input.trim();
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            if (step == 1) {
                dataType = normalizeDataTypeInput(input);
                step = 2;
                return ConsoleCommandResult.ok(List.of());
            }
            List<ValueSetEntryInput> entries = parseValueSetEntries(input);
            commandClient.createValueSet(backendSessionId, azName, dataType, entries);
            complete = true;
            return ConsoleCommandResult.ok(List.of("ValueSet " + azName + " added."));
        }
    }

    private final class AttachAttributeValueSetFlow extends BasePromptFlow {
        private String attributeAzName;

        @Override
        public String prompt() {
            return step == 0 ? "Attribute number or azName: " : "ValueSet azName: ";
        }

        @Override
        public ConsoleCommandResult accept(String input) throws IOException, InterruptedException {
            ArrayList<String> output = new ArrayList<>();
            if (step == 0) {
                attributeAzName = resolveAttributeReference(input, output);
                if (attributeAzName == null) {
                    complete = true;
                    return ConsoleCommandResult.ok(output);
                }
                step = 1;
                return ConsoleCommandResult.ok(List.of());
            }
            if (input == null || input.isBlank()) {
                complete = true;
                return ConsoleCommandResult.ok(List.of("ValueSet azName is required."));
            }
            String valueSetAzName = input.trim();
            commandClient.setAttributeValueSet(backendSessionId, attachedEntityAzName, attributeAzName, valueSetAzName);
            latestAttributes = modelClient.listAttributes(attachedModelAzName, attachedEntityAzName);
            complete = true;
            return ConsoleCommandResult.ok(List.of("Attribute " + attributeAzName + " now references ValueSet " + valueSetAzName + "."));
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
