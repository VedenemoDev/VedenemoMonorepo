package org.vedenemo.cli;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.ConsoleCapabilities;
import org.vedenemo.console.ConsoleCommandResult;
import org.vedenemo.console.ConsoleSession;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SessionClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class VedenemoCliApp {

    private final SessionClient sessionClient;
    private final ModelClient modelClient;
    private final CommandClient commandClient;
    private final InputStream input;
    private final PrintStream output;
    private final boolean registerShutdownHook;
    private final Path workingDirectory;

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
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        ConsoleSession consoleSession = new ConsoleSession(
                sessionId,
                modelClient,
                sessionClient,
                commandClient,
                ConsoleCapabilities.terminal()
        );
        while (true) {
            output.print(consoleSession.prompt());
            output.flush();
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                output.println();
                continue;
            }
            if ("exit".equals(trimmed)) {
                break;
            }
            handleCommand(consoleSession, reader, trimmed);
        }
    }

    private void handleCommand(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        if ("help".equals(line)) {
            printHelp();
        } else if ("add".equals(line)) {
            add(consoleSession, reader);
        } else if (line.startsWith("entity")) {
            handleEntityCommand(consoleSession, reader, line);
        } else if ("attributes".equals(line)) {
            executeSharedConsoleCommand(consoleSession, line);
        } else if (line.startsWith("attr")) {
            handleAttributeCommand(consoleSession, reader, line);
        } else if (line.equals("save") || line.startsWith("save ")) {
            save(consoleSession, reader, line);
        } else if (line.equals("load") || line.startsWith("load ")) {
            load(consoleSession, reader, line);
        } else if (line.startsWith("attach")) {
            attachModel(consoleSession, reader, line);
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
        output.println("  list - list existing models");
        output.println("  add - add a new model");
        output.println("  attach [N | azName] - attach to a listed model");
        output.println("  detach - detach from the current model");
        output.println("  entities - list entities in the attached model");
        output.println("  entity [N | azName] - select an entity in the attached model");
        output.println("  entity detach - clear the selected entity");
        output.println("  attributes - list attributes in the selected entity");
        output.println("  attr add - add an attribute to the selected entity");
        output.println("  undo - undo the latest backend command");
        output.println("  save [N | azName] [outputPath] - save a model to a .vdos file");
        output.println("  load <path> - load a model from a .vdos file");
        output.println("  help - show this help");
        output.println("  exit - end the session and exit");
    }

    private void attachModel(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        String argument = line.length() == "attach".length() ? "" : line.substring("attach".length()).trim();
        if (argument.isEmpty()) {
            output.print("Model number or azName: ");
            output.flush();
            String answer = reader.readLine();
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
                    .filter(model -> model.azName().equalsIgnoreCase(argument))
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

    private void add(ConsoleSession consoleSession, BufferedReader reader) throws IOException, InterruptedException {
        if (consoleSession.attachedModelAzName().isEmpty()) {
            addModel(consoleSession, reader);
        } else {
            addEntity(consoleSession, reader);
        }
    }

    private void addModel(ConsoleSession consoleSession, BufferedReader reader) throws IOException, InterruptedException {
        output.print("Model visible name: ");
        output.flush();
        String visName = reader.readLine();
        if (visName == null || visName.isBlank()) {
            output.println("Model visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            output.print("Model azName: ");
        } else {
            output.print("Model azName [" + suggestion + "]: ");
        }
        output.flush();
        String enteredAzName = reader.readLine();
        if (enteredAzName == null) {
            output.println("Model azName is required.");
            return;
        }
        if (enteredAzName.isBlank()) {
            if (suggestion == null) {
                output.println("Model azName is required.");
                return;
            }
            azName = suggestion;
        } else {
            azName = enteredAzName.trim();
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

    private void addEntity(ConsoleSession consoleSession, BufferedReader reader) throws IOException, InterruptedException {
        output.print("Entity visible name: ");
        output.flush();
        String visName = reader.readLine();
        if (visName == null || visName.isBlank()) {
            output.println("Entity visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            output.print("Entity azName: ");
        } else {
            output.print("Entity azName [" + suggestion + "]: ");
        }
        output.flush();
        String enteredAzName = reader.readLine();
        if (enteredAzName == null) {
            output.println("Entity azName is required.");
            return;
        }
        if (enteredAzName.isBlank()) {
            if (suggestion == null) {
                output.println("Entity azName is required.");
                return;
            }
            azName = suggestion;
        } else {
            azName = enteredAzName.trim();
        }
        try {
            commandClient.createEntity(consoleSession.backendSessionId(), azName, visName);
            output.println("Entity " + azName + " added.");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void handleEntityCommand(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        if (!line.equals("entity") && !line.startsWith("entity ")) {
            output.println("Unknown command: " + line);
            return;
        }
        String argument = line.length() == "entity".length() ? "" : line.substring("entity".length()).trim();
        if (argument.isEmpty()) {
            output.print("Entity number or azName: ");
            output.flush();
            String answer = reader.readLine();
            if (answer == null || answer.trim().isEmpty()) {
                output.println("No entity identifier entered.");
                return;
            }
            argument = answer.trim();
        }
        executeSharedConsoleCommand(consoleSession, "entity " + argument);
    }

    private void handleAttributeCommand(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        if ("attr add".equals(line)) {
            addAttribute(consoleSession, reader);
        } else {
            output.println("Unknown command: " + line);
        }
    }

    private void addAttribute(ConsoleSession consoleSession, BufferedReader reader) throws IOException, InterruptedException {
        Optional<String> entityAzName = consoleSession.attachedEntityAzName();
        if (consoleSession.attachedModelAzName().isEmpty()) {
            output.println("Attach a model before adding an attribute.");
            return;
        }
        if (entityAzName.isEmpty()) {
            output.println("Select an entity before adding an attribute.");
            return;
        }
        output.print("Attribute visible name: ");
        output.flush();
        String visName = reader.readLine();
        if (visName == null || visName.isBlank()) {
            output.println("Attribute visible name is required.");
            return;
        }
        String suggestion = suggestAzName(visName);
        String azName;
        if (suggestion == null) {
            output.print("Attribute azName: ");
        } else {
            output.print("Attribute azName [" + suggestion + "]: ");
        }
        output.flush();
        String enteredAzName = reader.readLine();
        if (enteredAzName == null) {
            output.println("Attribute azName is required.");
            return;
        }
        if (enteredAzName.isBlank()) {
            if (suggestion == null) {
                output.println("Attribute azName is required.");
                return;
            }
            azName = suggestion;
        } else {
            azName = enteredAzName.trim();
        }
        output.print("Attribute data type [TEXT]: ");
        output.flush();
        String enteredDataType = reader.readLine();
        String dataType = normalizeDataTypeInput(enteredDataType);
        try {
            commandClient.createAttribute(consoleSession.backendSessionId(), entityAzName.orElseThrow(), azName, visName, dataType);
            output.println("Attribute " + azName + " added.");
        } catch (IOException exception) {
            output.println("Attribute was not added: " + exception.getMessage() + ".");
        }
    }

    private void save(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        String argumentText = line.length() == "save".length() ? "" : line.substring("save".length()).trim();
        List<String> arguments = splitArguments(argumentText);
        if (arguments.size() > 2) {
            output.println("Usage: save [N | azName] [outputPath]");
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
            output.print("File " + target + " exists. Overwrite? [y/N]: ");
            output.flush();
            String answer = reader.readLine();
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

    private Path saveTargetPath(BufferedReader reader, ModelSummary model, String inlinePath) throws IOException {
        String selectedPath = inlinePath;
        if (selectedPath == null || selectedPath.isBlank()) {
            output.print("Output file [" + model.azName() + ".vdos]: ");
            output.flush();
            String answer = reader.readLine();
            selectedPath = answer == null || answer.isBlank() ? model.azName() + ".vdos" : answer.trim();
        }
        return resolvePathWithExtension(selectedPath);
    }

    private void load(ConsoleSession consoleSession, BufferedReader reader, String line) throws IOException, InterruptedException {
        String argument = line.length() == "load".length() ? "" : line.substring("load".length()).trim();
        if (argument.isBlank()) {
            output.println("Usage: load <path>");
            return;
        }
        Path source = resolvePathWithExtension(argument);
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

    private ModelImportResult importScriptWithRenamePrompt(BufferedReader reader, String script, String modelAzNameOverride)
            throws IOException, InterruptedException {
        try {
            return modelClient.importScript(script, modelAzNameOverride);
        } catch (ModelAlreadyExistsException exception) {
            output.println(exception.getMessage());
            output.print("New model azName for import, or blank to cancel: ");
            output.flush();
            String answer = reader.readLine();
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

    private Path resolvePathWithExtension(String value) {
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            path = workingDirectory.resolve(path);
        }
        if (path.getFileName() != null && path.getFileName().toString().indexOf('.') == -1) {
            path = path.resolveSibling(path.getFileName() + ".vdos");
        }
        return path.normalize();
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
            default -> value.trim();
        };
    }

}
