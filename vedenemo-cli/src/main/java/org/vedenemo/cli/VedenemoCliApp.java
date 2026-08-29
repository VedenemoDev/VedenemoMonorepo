package org.vedenemo.cli;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.ConsoleCapabilities;
import org.vedenemo.console.ConsoleCommandResult;
import org.vedenemo.console.ConsoleSession;
import org.vedenemo.console.AssociationSummary;
import org.vedenemo.console.DumpImportResult;
import org.vedenemo.console.DumpPrecheckResult;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelInstanceRootSummary;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SessionClient;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class VedenemoCliApp {

    private static final String SNAPSHOT_DIRECTORY = ".vedenemo";
    private static final String DUMP_EXTENSION = ".vdmp";

    private final SessionClient sessionClient;
    private final ModelClient modelClient;
    private final CommandClient commandClient;
    private final InputStream input;
    private final PrintStream output;
    private final boolean registerShutdownHook;
    private final Path workingDirectory;
    private List<Path> latestSnapshotFiles = List.of();
    private List<Path> latestDumpFiles = List.of();
    private List<ModelInstanceRootSummary> latestInstanceRoots = List.of();

    public VedenemoCliApp(
            SessionClient sessionClient,
            ModelClient modelClient,
            CommandClient commandClient,
            InputStream input,
            PrintStream output,
            boolean registerShutdownHook
    ) {
        this(sessionClient, modelClient, commandClient, input, output, registerShutdownHook, Path.of("").toAbsolutePath());
    }

    VedenemoCliApp(
            SessionClient sessionClient,
            ModelClient modelClient,
            CommandClient commandClient,
            InputStream input,
            PrintStream output,
            boolean registerShutdownHook,
            Path workingDirectory
    ) {
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
        this.commandClient = Objects.requireNonNull(commandClient, "commandClient must not be null");
        this.input = Objects.requireNonNull(input, "input must not be null");
        this.output = Objects.requireNonNull(output, "output must not be null");
        this.registerShutdownHook = registerShutdownHook;
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
    }

    public int run() {
        AtomicReference<UUID> activeSessionId = new AtomicReference<>();
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        try {
            UUID sessionId = sessionClient.startSession();
            activeSessionId.set(sessionId);
            if (registerShutdownHook) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanup(activeSessionId, cleanedUp)));
            }
            output.println("Session with UUID " + sessionId + " is created / attached to.");
            runPromptLoop(sessionId);
            cleanup(activeSessionId, cleanedUp);
            return 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            output.println("Vedenemo CLI failed: " + exception.getMessage());
            cleanup(activeSessionId, cleanedUp);
            return 1;
        }
    }

    private void runPromptLoop(UUID sessionId) throws IOException, InterruptedException {
        ConsoleSession consoleSession = new ConsoleSession(
                sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.terminal()
        );
        try (CliInputReader reader = createInputReader()) {
            while (true) {
                String line = reader.readCommandLine(consoleSession.prompt());
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    output.println();
                    continue;
                }
                if ("exit".equals(commandName(trimmed).toLowerCase()) && commandOnly(trimmed)) {
                    break;
                }
                try {
                    handleCommand(consoleSession, reader, trimmed);
                } catch (PromptCancelledException exception) {
                    output.println("Operation cancelled.");
                }
            }
        }
    }

    private CliInputReader createInputReader() {
        if (input == System.in && System.console() != null) {
            try {
                return new TerminalCliInputReader(output);
            } catch (IOException exception) {
                output.println("Terminal line editing disabled: " + exception.getMessage());
            }
        }
        return new BufferedCliInputReader(input, output);
    }

    private void handleCommand(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        String command = commandName(line).toLowerCase();
        if ("help".equals(command) && commandOnly(line)) {
            printHelp();
        } else if ("add".equals(command) && commandOnly(line)) {
            add(consoleSession, reader);
        } else if ("entity".equals(command)) {
            handleEntityCommand(consoleSession, reader, line);
        } else if ("attributes".equals(command) && commandOnly(line)) {
            executeSharedConsoleCommand(consoleSession, line);
        } else if ("associations".equals(command) && commandOnly(line)) {
            executeSharedConsoleCommand(consoleSession, line);
        } else if ("assoc".equals(command)) {
            handleAssociationCommand(consoleSession, reader, line);
        } else if ("attr".equals(command)) {
            handleAttributeCommand(consoleSession, reader, line);
        } else if ("snapshots".equals(command) && commandOnly(line)) {
            snapshots();
        } else if ("roots".equals(command) && commandOnly(line)) {
            roots(consoleSession);
        } else if ("dumps".equals(command) && commandOnly(line)) {
            dumps();
        } else if ("msave".equals(command)) {
            save(consoleSession, reader, line);
        } else if ("mload".equals(command)) {
            load(consoleSession, reader, line);
        } else if ("dsave".equals(command)) {
            saveDump(consoleSession, reader, line);
        } else if ("dload".equals(command)) {
            loadDump(consoleSession, reader, line);
        } else if ("attach".equals(command)) {
            latestInstanceRoots = List.of();
            attachModel(consoleSession, reader, line);
        } else if ("detach".equals(command) && commandOnly(line)) {
            latestInstanceRoots = List.of();
            executeSharedConsoleCommand(consoleSession, line);
        } else {
            executeSharedConsoleCommand(consoleSession, line);
        }
    }

    private void executeSharedConsoleCommand(ConsoleSession consoleSession, String line) {
        ConsoleCommandResult result = consoleSession.execute(line);
        for (String outputLine : result.outputLines()) {
            output.println(outputLine);
        }
    }

    private void printHelp() {
        output.println("Available commands:");
        output.println("  ping - check backend connectivity");
        output.println("  list - list existing models");
        output.println("  add - add a new model");
        output.println("  attach [N | azName] - attach to a listed model");
        output.println("  detach - detach from the current model");
        output.println("  entities - list entities in the attached model");
        output.println("  entity [N | azName] - select an entity in the attached model");
        output.println("  entity detach - clear the selected entity");
        output.println("  attributes - list attributes in the selected entity");
        output.println("  attr add - add an attribute to the selected entity");
        output.println("  associations - list model associations, or selected entity associations");
        output.println("  assoc add [ownership | reference | relation] - add an association or relation");
        output.println("  undo - undo the latest backend command");
        output.println("  msave [N | azName] [outputPath] - save a model to a .vdos file");
        output.println("  snapshots - list .vdos files from the .vedenemo directory");
        output.println("  mload <path | snapshot-number> - load a model from a .vdos file");
        output.println("  roots - list model-instance roots for the attached model");
        output.println("  dumps - list .vdmp files from the .vedenemo directory");
        output.println("  dsave [root-id | root-number | root-name] [outputPath] - save a model-instance root to a .vdmp file");
        output.println("  dload <path | dump-number> - load a .vdmp file into a new model-instance root");
        output.println("  help - show this help");
        output.println("  exit - end the session and exit");
        output.println("  Esc - cancel the current interactive prompt");
    }

    private void attachModel(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        String argument = line.length() == "attach".length() ? "" : line.substring("attach".length()).trim();
        if (argument.isEmpty()) {
            String answer = reader.readLine("Model number or azName: ");
            if (answer == null || answer.trim().isEmpty()) {
                output.println("No model identifier entered.");
                return;
            }
            argument = answer.trim();
        }
        executeSharedConsoleCommand(consoleSession, "attach " + argument);
    }

    private Optional<ModelSummary> resolveModel(ConsoleSession consoleSession, String argument) throws InterruptedException {
        if (isPositiveInteger(argument)) {
            List<ModelSummary> models = consoleSession.latestModels();
            if (models.isEmpty()) {
                output.println("Run list first before attaching by number.");
                return Optional.empty();
            }
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= models.size()) {
                output.println("No model found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(models.get(index));
        }
        try {
            List<ModelSummary> models = modelClient.listModels();
            return models.stream()
                    .filter(model -> model.azName().equals(argument))
                    .findFirst()
                    .or(() -> {
                        output.println("No model found with azName " + argument + ".");
                        return Optional.empty();
                    });
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return Optional.empty();
        }
    }

    private void add(ConsoleSession consoleSession, CliInputReader reader) throws IOException, InterruptedException {
        if (consoleSession.attachedModelAzName().isEmpty()) {
            addModel(consoleSession, reader);
        } else {
            addEntity(consoleSession, reader);
        }
    }

    private void addModel(ConsoleSession consoleSession, CliInputReader reader) throws IOException, InterruptedException {
        String visName = reader.readLine("Model visible name: ");
        if (visName == null || visName.isBlank()) {
            output.println("Model visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            String enteredAzName = reader.readLine("Model azName: ");
            if (enteredAzName == null) {
                output.println("Model azName is required.");
                return;
            }
            azName = enteredAzName.trim();
        } else {
            String enteredAzName = reader.readLine("Model azName [" + suggestion + "]: ");
            if (enteredAzName == null) {
                output.println("Model azName is required.");
                return;
            }
            azName = enteredAzName.isBlank() ? suggestion : enteredAzName.trim();
        }
        if (azName.isBlank()) {
            output.println("Model azName is required.");
            return;
        }
        try {
            ModelSummary created = modelClient.addModel(azName, visName, "1.0.0");
            consoleSession.attachInitialModel(created.azName());
            consoleSession.refreshModels();
            output.println("Attached to model " + created.azName() + ".");
            output.println("Added and attached model " + created.azName() + ".");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void addEntity(ConsoleSession consoleSession, CliInputReader reader) throws IOException, InterruptedException {
        String visName = reader.readLine("Entity visible name: ");
        if (visName == null || visName.isBlank()) {
            output.println("Entity visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            String enteredAzName = reader.readLine("Entity azName: ");
            if (enteredAzName == null) {
                output.println("Entity azName is required.");
                return;
            }
            azName = enteredAzName.trim();
        } else {
            String enteredAzName = reader.readLine("Entity azName [" + suggestion + "]: ");
            if (enteredAzName == null) {
                output.println("Entity azName is required.");
                return;
            }
            azName = enteredAzName.isBlank() ? suggestion : enteredAzName.trim();
        }
        if (azName.isBlank()) {
            output.println("Entity azName is required.");
            return;
        }
        try {
            commandClient.createEntity(consoleSession.backendSessionId(), azName, visName);
            output.println("Entity " + azName + " added.");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void handleEntityCommand(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        String argument = line.length() == "entity".length() ? "" : line.substring("entity".length()).trim();
        if (argument.isEmpty()) {
            String answer = reader.readLine("Entity number or azName: ");
            if (answer == null || answer.trim().isEmpty()) {
                output.println("No entity identifier entered.");
                return;
            }
            argument = answer.trim();
        }
        executeSharedConsoleCommand(consoleSession, "entity " + argument);
    }

    private void handleAttributeCommand(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        if ("add".equals(argumentText(line, "attr").toLowerCase())) {
            addAttribute(consoleSession, reader);
        } else {
            output.println("Unknown command: " + line);
        }
    }

    private void handleAssociationCommand(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        List<String> arguments = splitArguments(argumentText(line, "assoc"));
        if (arguments.isEmpty() || !"add".equalsIgnoreCase(arguments.getFirst())) {
            output.println("Usage: assoc add [ownership | reference | relation]");
            return;
        }
        if (arguments.size() > 2) {
            output.println("Usage: assoc add [ownership | reference | relation]");
            return;
        }
        String kind = arguments.size() == 2 ? normalizeAssociationKind(arguments.get(1)) : null;
        if (kind == null) {
            String enteredKind = reader.readLine("Association kind [1 ownership, 2 reference, 3 relation]: ");
            kind = normalizeAssociationKind(enteredKind);
        }
        if (kind == null) {
            output.println("Association kind is required.");
            return;
        }
        if ("relation".equals(kind)) {
            addRelation(consoleSession, reader);
        } else {
            addAssociation(consoleSession, reader, kind);
        }
    }

    private void addAssociation(ConsoleSession consoleSession, CliInputReader reader, String kind) throws IOException, InterruptedException {
        Optional<String> modelAzName = consoleSession.attachedModelAzName();
        if (modelAzName.isEmpty()) {
            output.println("Attach a model before adding an association.");
            return;
        }
        String source = readEntityReference(consoleSession, reader, "Source entity number or azName: ");
        if (source == null) {
            return;
        }
        String target = readEntityReference(consoleSession, reader, "Target entity number or azName: ");
        if (target == null) {
            return;
        }
        String visName = reader.readLine("Association visible name: ");
        if (visName == null || visName.isBlank()) {
            output.println("Association visible name is required.");
            return;
        }
        String enteredCardinality = reader.readLine("Association cardinality [1]: ");
        String cardinality = enteredCardinality == null || enteredCardinality.isBlank() ? "1" : enteredCardinality.trim();
        String suggestion = suggestAssociationAzName(modelAzName.orElseThrow(), kind, source, target, visName);
        String enteredAzName = reader.readLine("Association azName [" + suggestion + "]: ");
        if (enteredAzName == null) {
            output.println("Association azName is required.");
            return;
        }
        String azName = enteredAzName.isBlank() ? suggestion : enteredAzName.trim();
        if (azName.isBlank()) {
            output.println("Association azName is required.");
            return;
        }
        try {
            commandClient.createAssociation(
                    consoleSession.backendSessionId(),
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
            output.println("Association " + azName + " added.");
        } catch (IOException exception) {
            output.println("Association was not added: " + exception.getMessage() + ".");
        }
    }

    private void addRelation(ConsoleSession consoleSession, CliInputReader reader) throws IOException, InterruptedException {
        Optional<String> modelAzName = consoleSession.attachedModelAzName();
        if (modelAzName.isEmpty()) {
            output.println("Attach a model before adding a relation.");
            return;
        }
        String source = readEntityReference(consoleSession, reader, "First end entity number or azName: ");
        if (source == null) {
            return;
        }
        String sourceRole = reader.readLine("First end role name: ");
        if (sourceRole == null || sourceRole.isBlank()) {
            output.println("First end role name is required.");
            return;
        }
        String sourceCardinality = readCardinality(reader, "First end cardinality [1]: ");
        String target = readEntityReference(consoleSession, reader, "Second end entity number or azName: ");
        if (target == null) {
            return;
        }
        String targetRole = reader.readLine("Second end role name: ");
        if (targetRole == null || targetRole.isBlank()) {
            output.println("Second end role name is required.");
            return;
        }
        String targetCardinality = readCardinality(reader, "Second end cardinality [1]: ");
        String visName = reader.readLine("Relation visible name: ");
        if (visName == null || visName.isBlank()) {
            output.println("Relation visible name is required.");
            return;
        }
        String suggestion = suggestAssociationAzName(modelAzName.orElseThrow(), "relation", source, target, visName);
        String enteredAzName = reader.readLine("Relation azName [" + suggestion + "]: ");
        if (enteredAzName == null) {
            output.println("Relation azName is required.");
            return;
        }
        String azName = enteredAzName.isBlank() ? suggestion : enteredAzName.trim();
        if (azName.isBlank()) {
            output.println("Relation azName is required.");
            return;
        }
        try {
            commandClient.createAssociation(
                    consoleSession.backendSessionId(),
                    "relation",
                    azName,
                    visName,
                    source,
                    target,
                    targetCardinality,
                    sourceRole.trim(),
                    targetRole.trim(),
                    sourceCardinality,
                    targetCardinality
            );
            output.println("Relation " + azName + " added.");
        } catch (IOException exception) {
            output.println("Relation was not added: " + exception.getMessage() + ".");
        }
    }

    private String readCardinality(CliInputReader reader, String prompt) throws IOException {
        String value = reader.readLine(prompt);
        return value == null || value.isBlank() ? "1" : value.trim();
    }

    private String readEntityReference(ConsoleSession consoleSession, CliInputReader reader, String prompt) throws IOException {
        String answer = reader.readLine(prompt);
        if (answer == null || answer.trim().isEmpty()) {
            output.println("Entity identifier is required.");
            return null;
        }
        String value = answer.trim();
        if (!isPositiveInteger(value)) {
            return value;
        }
        List<EntitySummary> entities = consoleSession.latestEntities();
        if (entities.isEmpty()) {
            output.println("Run entities first before selecting an entity by number.");
            return null;
        }
        int index = Integer.parseInt(value) - 1;
        if (index < 0 || index >= entities.size()) {
            output.println("No entity found for list number " + value + ".");
            return null;
        }
        return entities.get(index).azName();
    }

    private String suggestAssociationAzName(String modelAzName, String kind, String source, String target, String visName) throws IOException, InterruptedException {
        List<AssociationSummary> associations = modelClient.listAssociations(modelAzName);
        String base = source + "_" + Objects.requireNonNullElse(suggestAzName(visName), "");
        String suggestion = cleanAssociationSuggestion(base);
        if (suggestion == null) {
            suggestion = cleanAssociationSuggestion(source + "_" + target);
        }
        if (suggestion == null) {
            suggestion = "Association";
        }
        if (isAssociationAzNameAvailable(associations, suggestion)) {
            return suggestion;
        }
        String withTarget = cleanAssociationSuggestion(source + "_" + target);
        if (withTarget != null && isAssociationAzNameAvailable(associations, withTarget)) {
            return withTarget;
        }
        String withKind = cleanAssociationSuggestion(source + "_" + kind + "_" + target);
        if (withKind != null && isAssociationAzNameAvailable(associations, withKind)) {
            return withKind;
        }
        int suffix = 2;
        while (!isAssociationAzNameAvailable(associations, suggestion + "_" + suffix)) {
            suffix++;
        }
        return suggestion + "_" + suffix;
    }

    private static boolean isAssociationAzNameAvailable(List<AssociationSummary> associations, String azName) {
        return associations.stream().noneMatch(association -> association.azName().equalsIgnoreCase(azName));
    }

    private void addAttribute(ConsoleSession consoleSession, CliInputReader reader) throws IOException, InterruptedException {
        Optional<String> entityAzName = consoleSession.attachedEntityAzName();
        if (consoleSession.attachedModelAzName().isEmpty()) {
            output.println("Attach a model before adding an attribute.");
            return;
        }
        if (entityAzName.isEmpty()) {
            output.println("Select an entity before adding an attribute.");
            return;
        }
        String visName = reader.readLine("Attribute visible name: ");
        if (visName == null || visName.isBlank()) {
            output.println("Attribute visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            String enteredAzName = reader.readLine("Attribute azName: ");
            if (enteredAzName == null) {
                output.println("Attribute azName is required.");
                return;
            }
            azName = enteredAzName.trim();
        } else {
            String enteredAzName = reader.readLine("Attribute azName [" + suggestion + "]: ");
            if (enteredAzName == null) {
                output.println("Attribute azName is required.");
                return;
            }
            azName = enteredAzName.isBlank() ? suggestion : enteredAzName.trim();
        }
        if (azName.isBlank()) {
            output.println("Attribute azName is required.");
            return;
        }
        String enteredDataType = reader.readLine("Attribute data type [TEXT]: ");
        String dataType = normalizeDataTypeInput(enteredDataType);
        String enteredRequired = reader.readLine("Required? [n]: ");
        boolean required = parseYesNo(enteredRequired);
        try {
            commandClient.createAttribute(consoleSession.backendSessionId(), entityAzName.orElseThrow(), azName, visName, dataType, required, null);
            output.println("Attribute " + azName + " added.");
        } catch (IOException exception) {
            output.println("Attribute was not added: " + exception.getMessage() + ".");
        }
    }

    private void snapshots() {
        Path snapshotDirectory = snapshotDirectory();
        if (!Files.isDirectory(snapshotDirectory)) {
            latestSnapshotFiles = List.of();
            output.println("No .vedenemo directory found at " + snapshotDirectory + ".");
            return;
        }
        latestSnapshotFiles = listSnapshotFiles(snapshotDirectory);
        if (latestSnapshotFiles.isEmpty()) {
            output.println("No .vdos snapshots found in " + snapshotDirectory + ".");
            return;
        }
        for (int index = 0; index < latestSnapshotFiles.size(); index++) {
            output.println((index + 1) + ". " + latestSnapshotFiles.get(index).getFileName());
        }
    }

    private void save(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        String argumentText = line.length() == "msave".length() ? "" : line.substring("msave".length()).trim();
        List<String> arguments = splitArguments(argumentText);
        if (arguments.size() > 2) {
            output.println("Usage: msave [N | azName] [outputPath]");
            return;
        }
        Optional<ModelSummary> model = resolveSaveModel(consoleSession, arguments);
        if (model.isEmpty()) {
            return;
        }
        String script;
        try {
            script = modelClient.exportScript(model.orElseThrow().azName());
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return;
        }
        Path target = saveTargetPath(reader, model.orElseThrow(), arguments.size() == 2 ? arguments.get(1) : null);
        if (target == null) {
            return;
        }
        if (Files.exists(target)) {
            String answer = reader.readLine("File " + target + " exists. Overwrite? [y/N]: ");
            if (!"y".equalsIgnoreCase(answer == null ? "" : answer.trim())) {
                output.println("Save cancelled.");
                return;
            }
        }
        try {
            Files.writeString(target, script, StandardCharsets.UTF_8);
            output.println("Saved model " + model.orElseThrow().azName() + " to " + target + ".");
        } catch (IOException exception) {
            output.println("Save failed: " + exception.getMessage());
        }
    }

    private Optional<ModelSummary> resolveSaveModel(ConsoleSession consoleSession, List<String> arguments) throws InterruptedException {
        if (arguments.isEmpty()) {
            Optional<String> modelAzName = consoleSession.attachedModelAzName();
            if (modelAzName.isEmpty()) {
                output.println("Attach a model or provide a model number or azName before saving.");
                return Optional.empty();
            }
            return resolveModel(consoleSession, modelAzName.orElseThrow());
        }
        return resolveModel(consoleSession, arguments.getFirst());
    }

    private Path saveTargetPath(CliInputReader reader, ModelSummary model, String inlinePath) throws IOException {
        String selectedPath = inlinePath;
        if (selectedPath == null || selectedPath.isBlank()) {
            String defaultPath = defaultSavePathText(model);
            String answer = reader.readLine("Output file [" + defaultPath + "]: ");
            selectedPath = answer == null || answer.isBlank() ? model.azName() + ".vdos" : answer.trim();
        }
        return resolveSavePath(selectedPath);
    }

    private String defaultSavePathText(ModelSummary model) {
        String fileName = model.azName() + ".vdos";
        if (Files.isDirectory(snapshotDirectory())) {
            return SNAPSHOT_DIRECTORY + "/" + fileName;
        }
        return fileName;
    }

    private Path resolveSavePath(String value) {
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            Path baseDirectory = Files.isDirectory(snapshotDirectory()) ? snapshotDirectory() : workingDirectory;
            path = baseDirectory.resolve(path);
        }
        return addVdosExtension(path).normalize();
    }

    private Optional<ModelInstanceRootSummary> resolveDumpRoot(String argument) {
        if (latestInstanceRoots.isEmpty()) {
            output.println("No model-instance roots available.");
            return Optional.empty();
        }
        if (argument == null || argument.isBlank()) {
            if (latestInstanceRoots.size() == 1) {
                return Optional.of(latestInstanceRoots.getFirst());
            }
            output.println("Multiple model-instance roots are available. Provide a root number, root id, or root visible name.");
            for (int index = 0; index < latestInstanceRoots.size(); index++) {
                output.println(formatRoot(index, latestInstanceRoots.get(index)));
            }
            return Optional.empty();
        }
        String value = argument.trim();
        if (isPositiveInteger(value)) {
            int index = Integer.parseInt(value) - 1;
            if (index >= 0 && index < latestInstanceRoots.size()) {
                return Optional.of(latestInstanceRoots.get(index));
            }
            output.println("No model-instance root found for list number " + value + ".");
            return Optional.empty();
        }
        return latestInstanceRoots.stream()
                .filter(root -> root.instanceRootId().equals(value) || (root.visName() != null && root.visName().equals(value)))
                .findFirst()
                .or(() -> {
                    output.println("No model-instance root found for " + value + ".");
                    return Optional.empty();
                });
    }

    private Path dumpSaveTargetPath(
            CliInputReader reader,
            String modelAzName,
            ModelInstanceRootSummary root,
            String inlinePath
    ) throws IOException {
        String selectedPath = inlinePath;
        if (selectedPath == null || selectedPath.isBlank()) {
            String defaultFileName = defaultDumpFileName(modelAzName, root);
            String defaultPath = Files.isDirectory(snapshotDirectory()) ? SNAPSHOT_DIRECTORY + "/" + defaultFileName : defaultFileName;
            String answer = reader.readLine("Output dump file [" + defaultPath + "]: ");
            selectedPath = answer == null || answer.isBlank() ? defaultFileName : answer.trim();
        }
        return resolveDumpSavePath(selectedPath);
    }

    private String defaultDumpFileName(String modelAzName, ModelInstanceRootSummary root) {
        String rootPart = root.visName() == null || root.visName().isBlank() ? "root" : root.visName();
        String base = suggestAzName(modelAzName + "_" + rootPart + "_v" + root.modelVersion().replace('.', '_') + "_" + LocalDate.now());
        if (base == null || base.isBlank()) {
            base = modelAzName + "_dump";
        }
        return base + DUMP_EXTENSION;
    }

    private Path resolveDumpSavePath(String value) {
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            Path baseDirectory = Files.isDirectory(snapshotDirectory()) ? snapshotDirectory() : workingDirectory;
            path = baseDirectory.resolve(path);
        }
        return addDumpExtension(path).normalize();
    }

    private void roots(ConsoleSession consoleSession) throws IOException, InterruptedException {
        Optional<String> modelAzName = consoleSession.attachedModelAzName();
        if (modelAzName.isEmpty()) {
            output.println("Attach a model before listing model-instance roots.");
            return;
        }
        latestInstanceRoots = modelClient.listInstanceRoots(modelAzName.orElseThrow());
        if (latestInstanceRoots.isEmpty()) {
            output.println("No model-instance roots available for model " + modelAzName.orElseThrow() + ".");
            return;
        }
        output.println("Model-instance roots for model " + modelAzName.orElseThrow() + ":");
        for (int index = 0; index < latestInstanceRoots.size(); index++) {
            output.println(formatRoot(index, latestInstanceRoots.get(index)));
        }
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

    private void load(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        String argument = line.length() == "mload".length() ? "" : line.substring("mload".length()).trim();
        if (argument.isBlank()) {
            output.println("Usage: mload <path>");
            return;
        }
        Optional<Path> sourcePath = resolveLoadSource(argument);
        if (sourcePath.isEmpty()) {
            return;
        }
        Path source = sourcePath.orElseThrow();
        if (!Files.exists(source)) {
            output.println("File not found: " + source + ".");
            return;
        }
        String script;
        try {
            script = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            output.println("Load failed: " + exception.getMessage());
            return;
        }
        ModelImportResult result = importScriptWithRenamePrompt(reader, script, null);
        if (result == null) {
            return;
        }
        consoleSession.attachInitialModel(result.modelAzName());
        consoleSession.refreshModels();
        output.println("Attached to model " + result.modelAzName() + ".");
        output.println("Loaded model " + result.modelAzName() + " from " + source + " with " + result.commandCount() + " commands.");
    }

    private void dumps() {
        Path dumpDirectory = snapshotDirectory();
        if (!Files.isDirectory(dumpDirectory)) {
            latestDumpFiles = List.of();
            output.println("No .vedenemo directory found at " + dumpDirectory + ".");
            return;
        }
        latestDumpFiles = listFilesByExtension(dumpDirectory, DUMP_EXTENSION);
        if (latestDumpFiles.isEmpty()) {
            output.println("No .vdmp dumps found in " + dumpDirectory + ".");
            return;
        }
        for (int index = 0; index < latestDumpFiles.size(); index++) {
            output.println((index + 1) + ". " + latestDumpFiles.get(index).getFileName());
        }
    }

    private void saveDump(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        Optional<String> modelAzName = consoleSession.attachedModelAzName();
        if (modelAzName.isEmpty()) {
            output.println("Attach a model before saving a data dump.");
            return;
        }
        List<String> arguments = splitArguments(argumentText(line, "dsave"));
        if (arguments.size() > 2) {
            output.println("Usage: dsave [root-id | root-number | root-name] [outputPath]");
            return;
        }
        if (latestInstanceRoots.isEmpty()) {
            latestInstanceRoots = modelClient.listInstanceRoots(modelAzName.orElseThrow());
        }
        Optional<ModelInstanceRootSummary> root = resolveDumpRoot(arguments.isEmpty() ? "" : arguments.getFirst());
        if (root.isEmpty()) {
            return;
        }
        String dumpContent;
        try {
            dumpContent = modelClient.exportDump(modelAzName.orElseThrow(), root.orElseThrow().instanceRootId());
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return;
        }
        Path target = dumpSaveTargetPath(
                reader,
                modelAzName.orElseThrow(),
                root.orElseThrow(),
                arguments.size() == 2 ? arguments.get(1) : null
        );
        if (target == null) {
            return;
        }
        if (Files.exists(target)) {
            String answer = reader.readLine("File " + target + " exists. Overwrite? [y/N]: ");
            if (!"y".equalsIgnoreCase(answer == null ? "" : answer.trim())) {
                output.println("Dump save cancelled.");
                return;
            }
        }
        try {
            Files.writeString(target, dumpContent, StandardCharsets.UTF_8);
            output.println("Saved model-instance root " + root.orElseThrow().instanceRootId() + " to " + target + ".");
        } catch (IOException exception) {
            output.println("Dump save failed: " + exception.getMessage());
        }
    }

    private void loadDump(ConsoleSession consoleSession, CliInputReader reader, String line) throws IOException, InterruptedException {
        Optional<String> modelAzName = consoleSession.attachedModelAzName();
        if (modelAzName.isEmpty()) {
            output.println("Attach a model before loading a data dump.");
            return;
        }
        String argument = argumentText(line, "dload");
        if (argument.isBlank()) {
            output.println("Usage: dload <path | dump-number>");
            return;
        }
        Optional<Path> sourcePath = resolveDumpLoadSource(argument);
        if (sourcePath.isEmpty()) {
            return;
        }
        Path source = sourcePath.orElseThrow();
        if (!Files.exists(source)) {
            output.println("File not found: " + source + ".");
            return;
        }
        String dumpContent;
        try {
            dumpContent = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            output.println("Dump load failed: " + exception.getMessage());
            return;
        }
        DumpPrecheckResult precheck;
        try {
            precheck = modelClient.precheckDump(modelAzName.orElseThrow(), dumpContent);
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return;
        }
        precheck.warnings().forEach(warning -> output.println("Warning: " + warning));
        precheck.diagnostics().forEach(diagnostic -> output.println("Diagnostic: " + diagnostic));
        if (!precheck.importable()) {
            return;
        }
        boolean confirmed = false;
        if (precheck.confirmationRequired()) {
            String answer = reader.readLine("Load older dump into newer model? [y/N]: ");
            if (!"y".equalsIgnoreCase(answer == null ? "" : answer.trim())) {
                output.println("Dump load cancelled.");
                return;
            }
            confirmed = true;
        }
        try {
            DumpImportResult result = modelClient.importDump(modelAzName.orElseThrow(), dumpContent, confirmed);
            output.println("Loaded data dump from " + source + " into model-instance root " + result.root().instanceRootId() + ".");
            result.createdEntityCounts().forEach((entity, count) -> output.println("Created " + count + " " + entity + " records."));
            output.println("Created " + result.createdAssociationLinkCount() + " association links.");
            if (result.skippedDuplicateLinkCount() > 0) {
                output.println("Skipped " + result.skippedDuplicateLinkCount() + " duplicate association links.");
            }
            result.warnings().forEach(warning -> output.println("Warning: " + warning));
            result.failedInserts().forEach(failure -> output.println("Failed insert: " + failure));
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private Optional<Path> resolveLoadSource(String argument) {
        if (isPositiveInteger(argument) && !latestSnapshotFiles.isEmpty()) {
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestSnapshotFiles.size()) {
                output.println("No snapshot found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestSnapshotFiles.get(index));
        }
        return Optional.of(resolveLoadPath(argument));
    }

    private Path resolveLoadPath(String value) {
        Path enteredPath = Path.of(value.trim());
        Path resolvedPath = resolvePathWithExtension(value);
        if (!enteredPath.isAbsolute() && enteredPath.getParent() == null) {
            Path snapshotPath = resolvePathWithExtension(snapshotDirectory().resolve(value.trim()).toString());
            if (Files.exists(snapshotPath)) {
                return snapshotPath;
            }
        }
        return resolvedPath;
    }

    private Optional<Path> resolveDumpLoadSource(String argument) {
        if (isPositiveInteger(argument) && !latestDumpFiles.isEmpty()) {
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestDumpFiles.size()) {
                output.println("No dump found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestDumpFiles.get(index));
        }
        return Optional.of(resolveDumpLoadPath(argument));
    }

    private Path resolveDumpLoadPath(String value) {
        Path enteredPath = Path.of(value.trim());
        Path resolvedPath = resolvePathWithDumpExtension(value);
        if (!enteredPath.isAbsolute() && enteredPath.getParent() == null) {
            Path dumpPath = resolvePathWithDumpExtension(snapshotDirectory().resolve(value.trim()).toString());
            if (Files.exists(dumpPath)) {
                return dumpPath;
            }
        }
        return resolvedPath;
    }

    private ModelImportResult importScriptWithRenamePrompt(CliInputReader reader, String script, String modelAzNameOverride)
            throws IOException, InterruptedException {
        try {
            return modelClient.importScript(script, modelAzNameOverride);
        } catch (ModelAlreadyExistsException exception) {
            output.println(exception.getMessage());
            String answer = reader.readLine("New model azName for import, or blank to cancel: ");
            if (answer == null || answer.isBlank()) {
                output.println("Load cancelled.");
                return null;
            }
            return importScriptWithRenamePrompt(reader, script, answer.trim());
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return null;
        }
    }

    private void cleanup(AtomicReference<UUID> activeSessionId, AtomicBoolean cleanedUp) {
        UUID sessionId = activeSessionId.get();
        if (sessionId == null || !cleanedUp.compareAndSet(false, true)) {
            return;
        }
        try {
            sessionClient.endSession(sessionId);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            output.println("Session cleanup failed: " + exception.getMessage());
        }
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

    private Path resolvePathWithExtension(String value) {
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            path = workingDirectory.resolve(path);
        }
        return addVdosExtension(path).normalize();
    }

    private Path resolvePathWithDumpExtension(String value) {
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            path = workingDirectory.resolve(path);
        }
        return addDumpExtension(path).normalize();
    }

    private Path addVdosExtension(Path path) {
        if (path.getFileName() != null && path.getFileName().toString().indexOf('.') == -1) {
            path = path.resolveSibling(path.getFileName() + ".vdos");
        }
        return path;
    }

    private Path addDumpExtension(Path path) {
        if (path.getFileName() != null && path.getFileName().toString().indexOf('.') == -1) {
            path = path.resolveSibling(path.getFileName() + DUMP_EXTENSION);
        }
        return path;
    }

    private Path snapshotDirectory() {
        return workingDirectory.resolve(SNAPSHOT_DIRECTORY).normalize();
    }

    private static List<Path> listSnapshotFiles(Path snapshotDirectory) {
        return listFilesByExtension(snapshotDirectory, ".vdos");
    }

    private static List<Path> listFilesByExtension(Path snapshotDirectory, String extension) {
        ArrayList<Path> files = new ArrayList<>();
        try (var directoryStream = Files.newDirectoryStream(snapshotDirectory)) {
            for (Path path : directoryStream) {
                if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(extension)) {
                    files.add(path.normalize());
                }
            }
        } catch (IOException exception) {
            return List.of();
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(files);
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

    private static boolean parseYesNo(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "y", "yes", "true", "required", "pakollinen", "k", "kylla" -> true;
            default -> false;
        };
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

    private static String cleanAssociationSuggestion(String value) {
        String suggestion = suggestAzName(value);
        if (suggestion == null || suggestion.isBlank()) {
            return null;
        }
        return suggestion;
    }

    private interface CliInputReader extends Closeable {
        String readCommandLine(String prompt) throws IOException;

        String readLine(String prompt) throws IOException;
    }

    private static final class PromptCancelledException extends IOException {
    }

    private static final class BufferedCliInputReader implements CliInputReader {
        private final BufferedReader reader;
        private final PrintStream output;

        private BufferedCliInputReader(InputStream input, PrintStream output) {
            this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            this.output = output;
        }

        @Override
        public String readCommandLine(String prompt) throws IOException {
            output.print(prompt);
            output.flush();
            return reader.readLine();
        }

        @Override
        public String readLine(String prompt) throws IOException {
            output.print(prompt);
            output.flush();
            String line = reader.readLine();
            if (line != null && line.indexOf('\u001b') >= 0) {
                throw new PromptCancelledException();
            }
            return line;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private static final class TerminalCliInputReader implements CliInputReader {
        private static final char BACKSPACE = 8;
        private static final char DELETE = 127;
        private static final char CTRL_D = 4;
        private static final char CTRL_N = 14;
        private static final char CTRL_P = 16;
        private static final char ESCAPE = 27;

        private final PrintStream output;
        private final InputStream terminalInput;
        private final Reader terminalReader;
        private final List<String> commandHistory = new ArrayList<>();
        private final String savedTerminalSettings;
        private final AtomicBoolean restored = new AtomicBoolean(false);
        private final Thread terminalRestoreHook;
        private int commandHistoryIndex;

        private TerminalCliInputReader(PrintStream output) throws IOException {
            this.output = output;
            this.terminalInput = Files.newInputStream(Path.of("/dev/tty"));
            this.terminalReader = new InputStreamReader(terminalInput, StandardCharsets.UTF_8);
            this.savedTerminalSettings = runStty("stty -g < /dev/tty");
            runStty("stty -icanon -echo min 1 time 0 < /dev/tty");
            this.terminalRestoreHook = new Thread(this::restoreTerminal);
            Runtime.getRuntime().addShutdownHook(terminalRestoreHook);
        }

        @Override
        public String readCommandLine(String prompt) throws IOException {
            String line = readInteractiveLine(prompt, true);
            if (line != null && !line.isBlank()) {
                commandHistory.add(line);
                commandHistoryIndex = commandHistory.size();
            }
            return line;
        }

        @Override
        public String readLine(String prompt) throws IOException {
            return readInteractiveLine(prompt, false);
        }

        @Override
        public void close() throws IOException {
            try {
                restoreTerminal();
                try {
                    Runtime.getRuntime().removeShutdownHook(terminalRestoreHook);
                } catch (IllegalStateException exception) {
                    // JVM shutdown is already in progress, so the hook registry cannot be changed.
                }
            } finally {
                terminalReader.close();
                terminalInput.close();
            }
        }

        private void restoreTerminal() {
            if (!restored.compareAndSet(false, true)) {
                return;
            }
            try {
                runStty("stty " + savedTerminalSettings + " < /dev/tty");
            } catch (IOException exception) {
                output.println("Terminal settings restore failed: " + exception.getMessage());
            }
        }

        private String readInteractiveLine(String prompt, boolean historyEnabled) throws IOException {
            StringBuilder buffer = new StringBuilder();
            if (historyEnabled) {
                commandHistoryIndex = commandHistory.size();
            }
            output.print(prompt);
            output.flush();
            while (true) {
                int value = terminalReader.read();
                if (value < 0) {
                    return null;
                }
                char character = (char) value;
                if (character == '\r' || character == '\n') {
                    output.println();
                    return buffer.toString();
                }
                if (character == CTRL_D && buffer.isEmpty()) {
                    output.println();
                    return null;
                }
                if (historyEnabled && character == CTRL_P) {
                    replaceWithHistoryEntry(prompt, buffer, -1);
                    continue;
                }
                if (historyEnabled && character == CTRL_N) {
                    replaceWithHistoryEntry(prompt, buffer, 1);
                    continue;
                }
                if (!historyEnabled && character == ESCAPE) {
                    output.println();
                    throw new PromptCancelledException();
                }
                if (character == ESCAPE && historyEnabled) {
                    handleEscapeSequence(prompt, buffer);
                    continue;
                }
                if (character == BACKSPACE || character == DELETE) {
                    if (!buffer.isEmpty()) {
                        buffer.deleteCharAt(buffer.length() - 1);
                        output.print("\b \b");
                        output.flush();
                    }
                    continue;
                }
                if (character >= 32 && character != DELETE) {
                    buffer.append(character);
                    output.print(character);
                    output.flush();
                }
            }
        }

        private void handleEscapeSequence(String prompt, StringBuilder buffer) throws IOException {
            int second = terminalReader.read();
            int third = terminalReader.read();
            if (second == '[' && third == 'A') {
                replaceWithHistoryEntry(prompt, buffer, -1);
            } else if (second == '[' && third == 'B') {
                replaceWithHistoryEntry(prompt, buffer, 1);
            }
        }

        private void replaceWithHistoryEntry(String prompt, StringBuilder buffer, int direction) {
            if (commandHistory.isEmpty()) {
                return;
            }
            if (direction < 0) {
                commandHistoryIndex = Math.max(0, commandHistoryIndex - 1);
            } else {
                commandHistoryIndex = Math.min(commandHistory.size(), commandHistoryIndex + 1);
            }
            buffer.setLength(0);
            if (commandHistoryIndex < commandHistory.size()) {
                buffer.append(commandHistory.get(commandHistoryIndex));
            }
            output.print("\r");
            output.print(prompt);
            output.print(buffer);
            output.print("\033[K");
            output.flush();
        }

        private static String runStty(String command) throws IOException {
            try {
                Process process = new ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("stty failed: " + output);
                }
                return output;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("stty interrupted", exception);
            }
        }
    }

}
