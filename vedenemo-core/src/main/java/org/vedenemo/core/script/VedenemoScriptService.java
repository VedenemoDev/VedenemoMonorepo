package org.vedenemo.core.script;

import org.vedenemo.core.command.Command;
import org.vedenemo.core.command.CreateAssociationCommand;
import org.vedenemo.core.command.CreateAttributeCommand;
import org.vedenemo.core.command.CreateEntityCommand;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.AssociationKind;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ModelVersion;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.ReferenceAssociation;
import org.vedenemo.core.model.RelationAssociation;
import org.vedenemo.core.model.RelationEnd;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VedenemoScriptService {

    private static final String HEADER = "vedenemo-script 1";

    private final ModelRegistry modelRegistry;
    private final ModelCommandJournal commandJournal;

    public VedenemoScriptService(ModelRegistry modelRegistry, ModelCommandJournal commandJournal) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.commandJournal = Objects.requireNonNull(commandJournal, "commandJournal must not be null");
    }

    public String exportModel(String modelAzName) {
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IllegalArgumentException("model not found"));
        StringBuilder script = new StringBuilder();
        script.append(HEADER).append("\n\n");
        script.append("model azName=").append(modelRoot.azName())
                .append(" visName=").append(quote(modelRoot.visName()))
                .append(" version=").append(modelRoot.version())
                .append("\n\n");
        script.append("commands\n");
        for (Command command : commandJournal.listForModel(modelRoot.azName())) {
            script.append(commandLine(command, modelRoot.version())).append("\n");
        }
        script.append("\n");
        script.append("snapshot\n");
        for (VEntity entity : modelRoot.entities()) {
            script.append("entity azName=").append(entity.azName())
                    .append(" visName=").append(quote(entity.visName()))
                    .append(" activeSince=").append(entity.activeSince())
                    .append(" deprecatedSince=").append(versionOrNull(entity.deprecatedSince().orElse(null)))
                    .append("\n");
            for (VAttribute attribute : entity.attributes()) {
                script.append("attribute entity=").append(entity.azName())
                        .append(" azName=").append(attribute.azName())
                        .append(" visName=").append(quote(attribute.visName()))
                        .append(" dataType=").append(attribute.type().name())
                        .append(" activeSince=").append(attribute.activeSince())
                        .append(" deprecatedSince=").append(versionOrNull(attribute.deprecatedSince().orElse(null)))
                        .append("\n");
            }
        }
        for (Association association : modelRoot.associations()) {
            script.append("association azName=").append(association.azName())
                    .append(" visName=").append(quote(association.visName()))
                    .append(" kind=").append(association.kind().name())
                    .append(" source=").append(association.sourceEntityAzName())
                    .append(relationSourceEndSnapshotFields(association))
                    .append(" target=").append(association.targetEntityAzName())
                    .append(relationTargetEndSnapshotFields(association))
                    .append(" cardinality=").append(association.cardinality())
                    .append(" activeSince=").append(association.activeSince())
                    .append(" deprecatedSince=").append(versionOrNull(association.deprecatedSince().orElse(null)))
                    .append("\n");
        }
        return script.toString();
    }

    public VedenemoScriptImportResult importModel(String script, String modelAzNameOverride) {
        ParsedScript parsed = parse(script);
        String targetModelAzName = modelAzNameOverride == null || modelAzNameOverride.isBlank()
                ? parsed.modelAzName()
                : ModelRoot.create(modelAzNameOverride.trim(), parsed.modelVisName(), parsed.version().toString()).azName();
        if (modelRegistry.contains(targetModelAzName)) {
            throw new IllegalStateException("model already exists: " + targetModelAzName);
        }
        ModelRoot replayed = new ModelRoot(targetModelAzName, parsed.modelVisName(), parsed.version());
        ArrayList<Command> importedCommands = new ArrayList<>();
        for (Command command : parsed.commands()) {
            Command retargeted = retarget(command, targetModelAzName);
            apply(replayed, retargeted);
            importedCommands.add(retargeted);
        }
        validateSnapshot(replayed, parsed.snapshot(), targetModelAzName);
        modelRegistry.add(replayed);
        commandJournal.replaceForModel(targetModelAzName, importedCommands);
        return new VedenemoScriptImportResult(targetModelAzName, importedCommands.size());
    }

    private static String commandLine(Command command, ModelVersion modelVersion) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            return "create-entity model=" + createEntityCommand.modelAzName()
                    + " entity=" + createEntityCommand.entityAzName()
                    + " visName=" + quote(createEntityCommand.entityVisName())
                    + " activeSince=" + modelVersion;
        }
        if (command instanceof CreateAttributeCommand createAttributeCommand) {
            return "create-attribute model=" + createAttributeCommand.modelAzName()
                    + " entity=" + createAttributeCommand.entityAzName()
                    + " attribute=" + createAttributeCommand.attributeAzName()
                    + " visName=" + quote(createAttributeCommand.attributeVisName())
                    + " dataType=" + createAttributeCommand.dataType().name()
                    + " activeSince=" + modelVersion;
        }
        if (command instanceof CreateAssociationCommand createAssociationCommand) {
            String line = "create-association model=" + createAssociationCommand.modelAzName()
                    + " kind=" + createAssociationCommand.kind().name()
                    + " association=" + createAssociationCommand.associationAzName()
                    + " visName=" + quote(createAssociationCommand.associationVisName())
                    + " source=" + createAssociationCommand.sourceEntityAzName()
                    + relationSourceEndCommandFields(createAssociationCommand)
                    + " target=" + createAssociationCommand.targetEntityAzName()
                    + relationTargetEndCommandFields(createAssociationCommand)
                    + " cardinality=" + createAssociationCommand.cardinality()
                    + " activeSince=" + modelVersion;
            return line;
        }
        throw new IllegalArgumentException("unsupported export command: " + command.getClass().getSimpleName());
    }

    private static Command retarget(Command command, String modelAzName) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            return new CreateEntityCommand(modelAzName, createEntityCommand.entityAzName(), createEntityCommand.entityVisName());
        }
        if (command instanceof CreateAttributeCommand createAttributeCommand) {
            return new CreateAttributeCommand(
                    modelAzName,
                    createAttributeCommand.entityAzName(),
                    createAttributeCommand.attributeAzName(),
                    createAttributeCommand.attributeVisName(),
                    createAttributeCommand.dataType()
            );
        }
        if (command instanceof CreateAssociationCommand createAssociationCommand) {
            return new CreateAssociationCommand(
                    modelAzName,
                    createAssociationCommand.kind(),
                    createAssociationCommand.associationAzName(),
                    createAssociationCommand.associationVisName(),
                    createAssociationCommand.sourceEntityAzName(),
                    createAssociationCommand.targetEntityAzName(),
                    createAssociationCommand.cardinality(),
                    createAssociationCommand.sourceRoleName(),
                    createAssociationCommand.targetRoleName(),
                    createAssociationCommand.sourceCardinality(),
                    createAssociationCommand.targetCardinality()
            );
        }
        throw new IllegalArgumentException("unsupported import command: " + command.getClass().getSimpleName());
    }

    private static void apply(ModelRoot modelRoot, Command command) {
        if (command instanceof CreateEntityCommand createEntityCommand) {
            modelRoot.addEntity(new VEntity(createEntityCommand.entityAzName(), createEntityCommand.entityVisName(), modelRoot.version()));
            return;
        }
        if (command instanceof CreateAttributeCommand createAttributeCommand) {
            VEntity entity = findEntity(modelRoot, createAttributeCommand.entityAzName());
            entity.addAttribute(new VAttribute(
                    createAttributeCommand.attributeAzName(),
                    createAttributeCommand.attributeVisName(),
                    createAttributeCommand.dataType(),
                    modelRoot.version()
            ));
            return;
        }
        if (command instanceof CreateAssociationCommand createAssociationCommand) {
            modelRoot.addAssociation(toAssociation(createAssociationCommand, modelRoot));
            return;
        }
        throw new IllegalArgumentException("unsupported import command: " + command.getClass().getSimpleName());
    }

    private static ParsedScript parse(String script) {
        Objects.requireNonNull(script, "script must not be null");
        String[] lines = script.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("script header is invalid");
        }
        String section = "";
        String modelAzName = null;
        String modelVisName = null;
        ModelVersion version = null;
        ArrayList<Command> commands = new ArrayList<>();
        Snapshot snapshot = new Snapshot();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("commands".equals(line) || "snapshot".equals(line)) {
                section = line;
                continue;
            }
            String keyword = keyword(line);
            Map<String, String> values = values(line.substring(keyword.length()).trim());
            if ("model".equals(keyword)) {
                modelAzName = required(values, "azName", index);
                modelVisName = required(values, "visName", index);
                version = ModelVersion.parse(required(values, "version", index));
            } else if ("commands".equals(section)) {
                commands.add(parseCommand(keyword, values, index));
            } else if ("snapshot".equals(section)) {
                parseSnapshotLine(snapshot, keyword, values, index);
            } else {
                throw new IllegalArgumentException("script line " + (index + 1) + " appears outside a section");
            }
        }
        if (modelAzName == null || modelVisName == null || version == null) {
            throw new IllegalArgumentException("script model metadata is missing");
        }
        return new ParsedScript(modelAzName, modelVisName, version, List.copyOf(commands), snapshot);
    }

    private static Command parseCommand(String keyword, Map<String, String> values, int lineIndex) {
        return switch (keyword) {
            case "create-entity" -> new CreateEntityCommand(
                    required(values, "model", lineIndex),
                    required(values, "entity", lineIndex),
                    required(values, "visName", lineIndex)
            );
            case "create-attribute" -> new CreateAttributeCommand(
                    required(values, "model", lineIndex),
                    required(values, "entity", lineIndex),
                    required(values, "attribute", lineIndex),
                    required(values, "visName", lineIndex),
                    DataType.valueOf(required(values, "dataType", lineIndex))
            );
            case "create-association" -> new CreateAssociationCommand(
                    required(values, "model", lineIndex),
                    AssociationKind.valueOf(required(values, "kind", lineIndex)),
                    required(values, "association", lineIndex),
                    required(values, "visName", lineIndex),
                    required(values, "source", lineIndex),
                    required(values, "target", lineIndex),
                    Cardinality.parse(required(values, "cardinality", lineIndex)),
                    values.get("sourceRole"),
                    values.get("targetRole"),
                    parseOptionalCardinality(values.get("sourceCardinality")),
                    parseOptionalCardinality(values.get("targetCardinality"))
            );
            default -> throw new IllegalArgumentException("unsupported command on script line " + (lineIndex + 1));
        };
    }

    private static void parseSnapshotLine(Snapshot snapshot, String keyword, Map<String, String> values, int lineIndex) {
        if ("entity".equals(keyword)) {
            snapshot.entities.add(new SnapshotEntity(
                    required(values, "azName", lineIndex),
                    required(values, "visName", lineIndex),
                    ModelVersion.parse(required(values, "activeSince", lineIndex)),
                    parseNullableVersion(required(values, "deprecatedSince", lineIndex))
            ));
        } else if ("attribute".equals(keyword)) {
            snapshot.attributes.add(new SnapshotAttribute(
                    required(values, "entity", lineIndex),
                    required(values, "azName", lineIndex),
                    required(values, "visName", lineIndex),
                    DataType.valueOf(required(values, "dataType", lineIndex)),
                    ModelVersion.parse(required(values, "activeSince", lineIndex)),
                    parseNullableVersion(required(values, "deprecatedSince", lineIndex))
            ));
        } else if ("association".equals(keyword)) {
            snapshot.associations.add(new SnapshotAssociation(
                    required(values, "azName", lineIndex),
                    required(values, "visName", lineIndex),
                    AssociationKind.valueOf(required(values, "kind", lineIndex)),
                    required(values, "source", lineIndex),
                    required(values, "target", lineIndex),
                    Cardinality.parse(required(values, "cardinality", lineIndex)),
                    values.get("sourceRole"),
                    values.get("targetRole"),
                    parseOptionalCardinality(values.get("sourceCardinality")),
                    parseOptionalCardinality(values.get("targetCardinality")),
                    ModelVersion.parse(required(values, "activeSince", lineIndex)),
                    parseNullableVersion(required(values, "deprecatedSince", lineIndex))
            ));
        } else {
            throw new IllegalArgumentException("unsupported snapshot line " + (lineIndex + 1));
        }
    }

    private static void validateSnapshot(ModelRoot modelRoot, Snapshot snapshot, String targetModelAzName) {
        for (SnapshotEntity expected : snapshot.entities) {
            VEntity actual = findEntity(modelRoot, expected.azName());
            if (!actual.visName().equals(expected.visName())
                    || !actual.activeSince().equals(expected.activeSince())
                    || !actual.deprecatedSince().equals(Optional.ofNullable(expected.deprecatedSince()))) {
                throw new IllegalArgumentException("snapshot entity does not match replayed commands: " + expected.azName());
            }
        }
        int actualEntityCount = modelRoot.entities().size();
        if (actualEntityCount != snapshot.entities.size()) {
            throw new IllegalArgumentException("snapshot entity count does not match replayed commands for " + targetModelAzName);
        }
        for (SnapshotAttribute expected : snapshot.attributes) {
            VEntity entity = findEntity(modelRoot, expected.entityAzName());
            VAttribute actual = findAttribute(entity, expected.azName());
            if (!actual.visName().equals(expected.visName())
                    || actual.type() != expected.dataType()
                    || !actual.activeSince().equals(expected.activeSince())
                    || !actual.deprecatedSince().equals(Optional.ofNullable(expected.deprecatedSince()))) {
                throw new IllegalArgumentException("snapshot attribute does not match replayed commands: " + expected.azName());
            }
        }
        int actualAttributeCount = modelRoot.entities().stream()
                .mapToInt(entity -> entity.attributes().size())
                .sum();
        if (actualAttributeCount != snapshot.attributes.size()) {
            throw new IllegalArgumentException("snapshot attribute count does not match replayed commands for " + targetModelAzName);
        }
        for (SnapshotAssociation expected : snapshot.associations) {
            Association actual = findAssociation(modelRoot, expected.azName());
            if (!actual.visName().equals(expected.visName())
                    || actual.kind() != expected.kind()
                    || !VEntity.uniquenessKey(actual.sourceEntityAzName()).equals(VEntity.uniquenessKey(expected.sourceEntityAzName()))
                    || !VEntity.uniquenessKey(actual.targetEntityAzName()).equals(VEntity.uniquenessKey(expected.targetEntityAzName()))
                    || !actual.cardinality().equals(expected.cardinality())
                    || relationSnapshotEndsMismatch(actual, expected)
                    || !actual.activeSince().equals(expected.activeSince())
                    || !actual.deprecatedSince().equals(Optional.ofNullable(expected.deprecatedSince()))) {
                throw new IllegalArgumentException("snapshot association does not match replayed commands: " + expected.azName());
            }
        }
        if (modelRoot.associations().size() != snapshot.associations.size()) {
            throw new IllegalArgumentException("snapshot association count does not match replayed commands for " + targetModelAzName);
        }
    }

    private static VEntity findEntity(ModelRoot modelRoot, String entityAzName) {
        String targetKey = VEntity.uniquenessKey(entityAzName);
        return modelRoot.entities().stream()
                .filter(entity -> VEntity.uniquenessKey(entity.azName()).equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("entity not found: " + entityAzName));
    }

    private static VAttribute findAttribute(VEntity entity, String attributeAzName) {
        String targetKey = VAttribute.uniquenessKey(attributeAzName);
        return entity.attributes().stream()
                .filter(attribute -> VAttribute.uniquenessKey(attribute.azName()).equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("attribute not found: " + attributeAzName));
    }

    private static Association findAssociation(ModelRoot modelRoot, String associationAzName) {
        String targetKey = Association.uniquenessKey(associationAzName);
        return modelRoot.associations().stream()
                .filter(association -> Association.uniquenessKey(association.azName()).equals(targetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("association not found: " + associationAzName));
    }

    private static Association toAssociation(CreateAssociationCommand command, ModelRoot modelRoot) {
        if (command.kind() == AssociationKind.OWNERSHIP) {
            return new OwnershipAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    command.sourceEntityAzName(),
                    command.targetEntityAzName(),
                    command.cardinality(),
                    modelRoot.version()
            );
        }
        if (command.kind() == AssociationKind.REFERENCE) {
            return new ReferenceAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    command.sourceEntityAzName(),
                    command.targetEntityAzName(),
                    command.cardinality(),
                    modelRoot.version()
            );
        }
        if (command.kind() == AssociationKind.RELATION) {
            return new RelationAssociation(
                    command.associationAzName(),
                    command.associationVisName(),
                    new RelationEnd(command.sourceEntityAzName(), command.sourceRoleName(), command.sourceCardinality()),
                    new RelationEnd(command.targetEntityAzName(), command.targetRoleName(), command.targetCardinality()),
                    modelRoot.version()
            );
        }
        throw new IllegalArgumentException("unsupported association kind: " + command.kind());
    }

    private static String relationSourceEndCommandFields(CreateAssociationCommand command) {
        if (command.kind() != AssociationKind.RELATION) {
            return "";
        }
        return " sourceRole=" + quote(command.sourceRoleName())
                + " sourceCardinality=" + command.sourceCardinality();
    }

    private static String relationTargetEndCommandFields(CreateAssociationCommand command) {
        if (command.kind() != AssociationKind.RELATION) {
            return "";
        }
        return " targetRole=" + quote(command.targetRoleName())
                + " targetCardinality=" + command.targetCardinality();
    }

    private static String relationSourceEndSnapshotFields(Association association) {
        if (association.kind() != AssociationKind.RELATION) {
            return "";
        }
        return " sourceRole=" + quote(association.sourceRoleName())
                + " sourceCardinality=" + association.sourceCardinality();
    }

    private static String relationTargetEndSnapshotFields(Association association) {
        if (association.kind() != AssociationKind.RELATION) {
            return "";
        }
        return " targetRole=" + quote(association.targetRoleName())
                + " targetCardinality=" + association.targetCardinality();
    }

    private static boolean relationSnapshotEndsMismatch(Association actual, SnapshotAssociation expected) {
        if (actual.kind() != AssociationKind.RELATION) {
            return false;
        }
        return !Objects.equals(actual.sourceRoleName(), expected.sourceRoleName())
                || !Objects.equals(actual.targetRoleName(), expected.targetRoleName())
                || !Objects.equals(actual.sourceCardinality(), expected.sourceCardinality())
                || !Objects.equals(actual.targetCardinality(), expected.targetCardinality());
    }

    private static String keyword(String line) {
        int index = line.indexOf(' ');
        if (index == -1) {
            return line;
        }
        return line.substring(0, index);
    }

    private static Map<String, String> values(String input) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        int index = 0;
        while (index < input.length()) {
            while (index < input.length() && input.charAt(index) == ' ') {
                index++;
            }
            if (index >= input.length()) {
                break;
            }
            int equalsIndex = input.indexOf('=', index);
            if (equalsIndex == -1) {
                throw new IllegalArgumentException("script key/value pair is invalid");
            }
            String key = input.substring(index, equalsIndex);
            index = equalsIndex + 1;
            String value;
            if (index < input.length() && input.charAt(index) == '"') {
                StringBuilder builder = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < input.length()) {
                    char character = input.charAt(index++);
                    if (character == '\\') {
                        if (index >= input.length()) {
                            throw new IllegalArgumentException("script quoted value escape is invalid");
                        }
                        builder.append(input.charAt(index++));
                    } else if (character == '"') {
                        closed = true;
                        break;
                    } else {
                        builder.append(character);
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException("script quoted value is not closed");
                }
                value = builder.toString();
            } else {
                int endIndex = input.indexOf(' ', index);
                if (endIndex == -1) {
                    value = input.substring(index);
                    index = input.length();
                } else {
                    value = input.substring(index, endIndex);
                    index = endIndex + 1;
                }
            }
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> values, String key, int lineIndex) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key + " on script line " + (lineIndex + 1));
        }
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String versionOrNull(ModelVersion version) {
        if (version == null) {
            return "null";
        }
        return version.toString();
    }

    private static ModelVersion parseNullableVersion(String value) {
        if ("null".equals(value)) {
            return null;
        }
        return ModelVersion.parse(value);
    }

    private static Cardinality parseOptionalCardinality(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Cardinality.parse(value);
    }

    private record ParsedScript(
            String modelAzName,
            String modelVisName,
            ModelVersion version,
            List<Command> commands,
            Snapshot snapshot
    ) {
    }

    private static final class Snapshot {
        private final List<SnapshotEntity> entities = new ArrayList<>();
        private final List<SnapshotAttribute> attributes = new ArrayList<>();
        private final List<SnapshotAssociation> associations = new ArrayList<>();
    }

    private record SnapshotEntity(
            String azName,
            String visName,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
    }

    private record SnapshotAttribute(
            String entityAzName,
            String azName,
            String visName,
            DataType dataType,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
    }

    private record SnapshotAssociation(
            String azName,
            String visName,
            AssociationKind kind,
            String sourceEntityAzName,
            String targetEntityAzName,
            Cardinality cardinality,
            String sourceRoleName,
            String targetRoleName,
            Cardinality sourceCardinality,
            Cardinality targetCardinality,
            ModelVersion activeSince,
            ModelVersion deprecatedSince
    ) {
    }
}
