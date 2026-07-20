package org.vedenemo.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
    private final AtomicReference<String> attachedModelAzName = new AtomicReference<>();
    private final AtomicReference<String> attachedEntityAzName = new AtomicReference<>();
    private List<ModelSummary> latestModels = List.of();
    private List<EntitySummary> latestEntities = List.of();
    private List<AttributeSummary> latestAttributes = List.of();

    public VedenemoCliApp(
            SessionClient sessionClient,
            ModelClient modelClient,
            CommandClient commandClient,
            InputStream input,
            PrintStream output,
            boolean registerShutdownHook
    ) {
        this.sessionClient = Objects.requireNonNull(sessionClient, "sessionClient must not be null");
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
        this.commandClient = Objects.requireNonNull(commandClient, "commandClient must not be null");
        this.input = Objects.requireNonNull(input, "input must not be null");
        this.output = Objects.requireNonNull(output, "output must not be null");
        this.registerShutdownHook = registerShutdownHook;
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
        while (true) {
            output.print(prompt());
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
            handleCommand(sessionId, reader, trimmed);
        }
    }

    private void handleCommand(UUID sessionId, BufferedReader reader, String line) throws IOException, InterruptedException {
        if ("help".equals(line)) {
            printHelp();
        } else if ("list".equals(line)) {
            listModels();
        } else if ("entities".equals(line)) {
            listEntities();
        } else if ("attributes".equals(line)) {
            listAttributes();
        } else if ("add".equals(line)) {
            add(sessionId, reader);
        } else if ("detach".equals(line)) {
            detachModel(sessionId);
        } else if (line.startsWith("entity")) {
            handleEntityCommand(reader, line);
        } else if (line.startsWith("attr")) {
            handleAttributeCommand(sessionId, reader, line);
        } else if ("undo".equals(line)) {
            undo(sessionId);
        } else if (line.startsWith("attach")) {
            attachModel(sessionId, reader, line);
        } else {
            output.println("Unknown command: " + line);
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
        output.println("  help - show this help");
        output.println("  exit - end the session and exit");
    }

    private void listModels() throws InterruptedException {
        try {
            latestModels = modelClient.listModels();
            if (latestModels.isEmpty()) {
                output.println("No models available.");
                return;
            }
            for (int index = 0; index < latestModels.size(); index++) {
                ModelSummary model = latestModels.get(index);
                output.println((index + 1) + ". " + model.visName() + " (" + model.azName() + ") version " + model.version());
            }
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void listEntities() throws InterruptedException {
        String modelAzName = attachedModelAzName.get();
        if (modelAzName == null) {
            output.println("Attach a model before listing entities.");
            return;
        }
        try {
            latestEntities = modelClient.listEntities(modelAzName);
            if (latestEntities.isEmpty()) {
                output.println("No entities available.");
                return;
            }
            for (int index = 0; index < latestEntities.size(); index++) {
                EntitySummary entity = latestEntities.get(index);
                output.println((index + 1) + ". " + entity.visName() + " (" + entity.azName() + ") active since " + entity.activeSince());
            }
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void listAttributes() throws InterruptedException {
        String modelAzName = attachedModelAzName.get();
        String entityAzName = attachedEntityAzName.get();
        if (modelAzName == null) {
            output.println("Attach a model before listing attributes.");
            return;
        }
        if (entityAzName == null) {
            output.println("Select an entity before listing attributes.");
            return;
        }
        try {
            latestAttributes = modelClient.listAttributes(modelAzName, entityAzName);
            if (latestAttributes.isEmpty()) {
                output.println("No attributes available.");
                return;
            }
            for (int index = 0; index < latestAttributes.size(); index++) {
                AttributeSummary attribute = latestAttributes.get(index);
                output.println((index + 1) + ". "
                        + attribute.visName()
                        + " ("
                        + attribute.azName()
                        + ") type "
                        + attribute.dataType()
                        + " active since "
                        + attribute.activeSince()
                        + deprecatedSuffix(attribute.deprecatedSince()));
            }
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void attachModel(UUID sessionId, BufferedReader reader, String line) throws IOException, InterruptedException {
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
        Optional<ModelSummary> model = resolveModel(argument);
        if (model.isEmpty()) {
            return;
        }
        attachResolvedModel(sessionId, model.orElseThrow());
    }

    private Optional<ModelSummary> resolveModel(String argument) throws InterruptedException {
        if (isPositiveInteger(argument)) {
            if (latestModels.isEmpty()) {
                output.println("Run list first before attaching by number.");
                return Optional.empty();
            }
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestModels.size()) {
                output.println("No model found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestModels.get(index));
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

    private void add(UUID sessionId, BufferedReader reader) throws IOException, InterruptedException {
        if (attachedModelAzName.get() == null) {
            addModel(sessionId, reader);
        } else {
            addEntity(sessionId, reader);
        }
    }

    private void addModel(UUID sessionId, BufferedReader reader) throws IOException, InterruptedException {
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
            attachResolvedModel(sessionId, created);
            latestModels = modelClient.listModels();
            output.println("Added and attached model " + created.azName() + ".");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void addEntity(UUID sessionId, BufferedReader reader) throws IOException, InterruptedException {
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
            commandClient.createEntity(sessionId, azName, visName);
            output.println("Entity " + azName + " added.");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void attachResolvedModel(UUID sessionId, ModelSummary model) throws InterruptedException {
        try {
            sessionClient.selectModel(sessionId, model.azName());
            attachedModelAzName.set(model.azName());
            attachedEntityAzName.set(null);
            latestEntities = List.of();
            latestAttributes = List.of();
            output.println("Attached to model " + model.azName() + ".");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void detachModel(UUID sessionId) throws InterruptedException {
        if (attachedModelAzName.get() == null) {
            output.println("No model is currently attached.");
            return;
        }
        try {
            sessionClient.clearSelectedModel(sessionId);
            attachedModelAzName.set(null);
            attachedEntityAzName.set(null);
            latestEntities = List.of();
            latestAttributes = List.of();
            output.println("Detached from model.");
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private void handleEntityCommand(BufferedReader reader, String line) throws IOException, InterruptedException {
        if ("entity detach".equals(line)) {
            detachEntity();
            return;
        }
        if (!line.equals("entity") && !line.startsWith("entity ")) {
            output.println("Unknown command: " + line);
            return;
        }
        selectEntity(reader, line);
    }

    private void selectEntity(BufferedReader reader, String line) throws IOException, InterruptedException {
        if (attachedModelAzName.get() == null) {
            output.println("Attach a model before selecting an entity.");
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
        Optional<EntitySummary> entity = resolveEntity(argument);
        if (entity.isEmpty()) {
            return;
        }
        attachedEntityAzName.set(entity.orElseThrow().azName());
        latestAttributes = List.of();
        output.println("Selected entity " + entity.orElseThrow().azName() + ".");
    }

    private Optional<EntitySummary> resolveEntity(String argument) throws InterruptedException {
        if (isPositiveInteger(argument)) {
            if (latestEntities.isEmpty()) {
                output.println("Run entities first before selecting by number.");
                return Optional.empty();
            }
            int index = Integer.parseInt(argument) - 1;
            if (index < 0 || index >= latestEntities.size()) {
                output.println("No entity found for list number " + argument + ".");
                return Optional.empty();
            }
            return Optional.of(latestEntities.get(index));
        }
        try {
            List<EntitySummary> entities = modelClient.listEntities(attachedModelAzName.get());
            return entities.stream()
                    .filter(entity -> entity.azName().equalsIgnoreCase(argument))
                    .findFirst()
                    .or(() -> {
                        output.println("No entity found with azName " + argument + ".");
                        return Optional.empty();
                    });
        } catch (IOException exception) {
            output.println(exception.getMessage());
            return Optional.empty();
        }
    }

    private void detachEntity() {
        if (attachedEntityAzName.get() == null) {
            output.println("No entity is currently selected.");
            return;
        }
        attachedEntityAzName.set(null);
        latestAttributes = List.of();
        output.println("Entity detached.");
    }

    private void handleAttributeCommand(UUID sessionId, BufferedReader reader, String line) throws IOException, InterruptedException {
        if ("attr add".equals(line)) {
            addAttribute(sessionId, reader);
        } else {
            output.println("Unknown command: " + line);
        }
    }

    private void addAttribute(UUID sessionId, BufferedReader reader) throws IOException, InterruptedException {
        String entityAzName = attachedEntityAzName.get();
        if (attachedModelAzName.get() == null) {
            output.println("Attach a model before adding an attribute.");
            return;
        }
        if (entityAzName == null) {
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
            commandClient.createAttribute(sessionId, entityAzName, azName, visName, dataType);
            latestAttributes = List.of();
            output.println("Attribute " + azName + " added.");
        } catch (IOException exception) {
            output.println("Attribute was not added: " + exception.getMessage() + ".");
        }
    }

    private void undo(UUID sessionId) throws InterruptedException {
        try {
            UndoCommandResult result = commandClient.undo(sessionId);
            if (result.isNothingToUndo()) {
                output.println("Nothing to undo.");
            } else {
                output.println(undoMessage(result));
            }
        } catch (IOException exception) {
            output.println(exception.getMessage());
        }
    }

    private String prompt() {
        String azName = attachedModelAzName.get();
        if (azName == null) {
            return "VedenemoCli>";
        }
        String entityAzName = attachedEntityAzName.get();
        if (entityAzName == null) {
            return "VedenemoCli[" + azName + "]>";
        }
        return "VedenemoCli[" + azName + "/" + entityAzName + "]>";
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

    private static String deprecatedSuffix(String deprecatedSince) {
        if (deprecatedSince == null) {
            return "";
        }
        return " deprecated since " + deprecatedSince;
    }

    private static String undoMessage(UndoCommandResult result) {
        return switch (result.undoneCommand()) {
            case "create-entity" -> "Undo completed: removed entity "
                    + result.entityAzName()
                    + " from model "
                    + result.modelAzName()
                    + ".";
            case "create-attribute" -> "Undo completed: removed attribute "
                    + result.attributeAzName()
                    + " from entity "
                    + result.entityAzName()
                    + " in model "
                    + result.modelAzName()
                    + ".";
            default -> "Undo completed.";
        };
    }
}
