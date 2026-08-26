package org.vedenemo.core.instance.dump;

import org.vedenemo.core.instance.AssociationInstanceLink;
import org.vedenemo.core.instance.EntityInstance;
import org.vedenemo.core.instance.InstanceValue;
import org.vedenemo.core.instance.LocationValue;
import org.vedenemo.core.instance.ModelInstanceRoot;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.ModelVersion;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModelInstanceDumpService {

    private final ModelInstanceService instanceService;

    public ModelInstanceDumpService(ModelInstanceService instanceService) {
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
    }

    public ModelInstanceDump exportDump(String modelAzName, String instanceRootId, Instant savedAt) {
        ModelRoot modelRoot = instanceService.describeApi(modelAzName);
        ModelInstanceRoot root = instanceService.readRoot(modelRoot.azName(), instanceRootId);
        List<DumpEntityGroup> entities = new ArrayList<>();
        for (VEntity entity : modelRoot.entities()) {
            List<DumpEntityRecord> records = instanceService
                    .listEntityInstances(modelRoot.azName(), root.instanceRootId(), entity.azName(), Map.of())
                    .stream()
                    .map(instance -> toDumpRecord(entity, instance))
                    .toList();
            entities.add(new DumpEntityGroup(entity.azName(), records));
        }
        List<DumpAssociationLink> links = new ArrayList<>();
        for (Association association : modelRoot.associations()) {
            for (AssociationInstanceLink link : instanceService.listAssociationLinks(modelRoot.azName(), root.instanceRootId(), association.azName())) {
                links.add(new DumpAssociationLink(
                        association.azName(),
                        link.sourceInstanceId().value(),
                        link.targetInstanceId().value()
                ));
            }
        }
        return new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Objects.requireNonNull(savedAt, "savedAt must not be null"),
                new DumpModel(modelRoot.azName(), modelRoot.visName(), modelRoot.version().toString()),
                new DumpRoot(root.instanceRootId(), root.visName()),
                entities,
                links
        );
    }

    public ModelInstanceDumpPrecheckResult precheck(String targetModelAzName, ModelInstanceDump dump) {
        Objects.requireNonNull(dump, "dump must not be null");
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        ModelRoot targetModel;
        try {
            targetModel = instanceService.describeApi(targetModelAzName);
        } catch (IllegalArgumentException exception) {
            diagnostics.add("model not found: " + targetModelAzName);
            return new ModelInstanceDumpPrecheckResult(false, false, warnings, diagnostics);
        }
        if (!targetModel.azName().equals(dump.model().azName())) {
            diagnostics.add("dump model azName " + dump.model().azName() + " does not match target model " + targetModel.azName());
        }

        ModelVersion dumpVersion = null;
        try {
            dumpVersion = ModelVersion.parse(dump.model().version());
        } catch (IllegalArgumentException exception) {
            diagnostics.add("dump model version is invalid: " + dump.model().version());
        }
        boolean confirmationRequired = false;
        if (dumpVersion != null) {
            int versionComparison = dumpVersion.compareTo(targetModel.version());
            if (versionComparison > 0) {
                diagnostics.add("dump model version " + dumpVersion + " is newer than loaded model version " + targetModel.version());
            } else if (versionComparison < 0) {
                confirmationRequired = true;
                warnings.add("dump model version " + dumpVersion + " is older than loaded model version " + targetModel.version());
            }
        }

        Map<String, VEntity> entities = entitiesByKey(targetModel);
        for (DumpEntityGroup group : dump.entities()) {
            VEntity entity = entities.get(VEntity.uniquenessKey(group.entityAzName()));
            if (entity == null) {
                diagnostics.add("entity not found: " + group.entityAzName());
                continue;
            }
            Map<String, VAttribute> attributes = attributesByKey(entity);
            for (DumpEntityRecord record : group.records()) {
                for (Map.Entry<String, Object> entry : record.values().entrySet()) {
                    VAttribute attribute = attributes.get(VAttribute.uniquenessKey(entry.getKey()));
                    if (attribute == null) {
                        diagnostics.add("attribute not found: " + group.entityAzName() + "." + entry.getKey());
                        continue;
                    }
                    if (entry.getValue() != null) {
                        if (!valueFitsType(attribute.type(), entry.getValue())) {
                            diagnostics.add("attribute type mismatch: " + group.entityAzName() + "." + attribute.azName()
                                    + " expects " + attribute.type() + " but dump value is " + entry.getValue().getClass().getSimpleName());
                        } else if (!valueFitsValueSet(targetModel, attribute, entry.getValue())) {
                            diagnostics.add("attribute value outside ValueSet: " + group.entityAzName() + "." + attribute.azName()
                                    + " must be one of " + attribute.valueSetAzName());
                        }
                    }
                }
            }
        }
        Map<String, Association> associations = associationsByKey(targetModel);
        for (DumpAssociationLink link : dump.links()) {
            if (!associations.containsKey(Association.uniquenessKey(link.associationAzName()))) {
                diagnostics.add("association not found: " + link.associationAzName());
            }
        }
        return new ModelInstanceDumpPrecheckResult(diagnostics.isEmpty(), confirmationRequired, warnings, diagnostics);
    }

    public ModelInstanceDumpImportResult importDump(String targetModelAzName, ModelInstanceDump dump, boolean confirmedVersionMismatch) {
        ModelInstanceDumpPrecheckResult precheck = precheck(targetModelAzName, dump);
        if (!precheck.importable()) {
            throw new IllegalArgumentException(String.join("; ", precheck.diagnostics()));
        }
        if (precheck.confirmationRequired() && !confirmedVersionMismatch) {
            throw new IllegalArgumentException("dump model version mismatch requires confirmation");
        }

        ModelInstanceRoot root = instanceService.createRoot(targetModelAzName, dump.root().visName());
        LinkedHashMap<String, Integer> createdEntityCounts = new LinkedHashMap<>();
        LinkedHashMap<String, String> importedIdsByDumpId = new LinkedHashMap<>();
        ArrayList<String> failedInserts = new ArrayList<>();

        for (DumpEntityGroup group : dump.entities()) {
            int created = 0;
            for (DumpEntityRecord record : group.records()) {
                try {
                    EntityInstance instance = instanceService.createEntityInstance(
                            targetModelAzName,
                            root.instanceRootId(),
                            group.entityAzName(),
                            submittedValues(record.values())
                    );
                    importedIdsByDumpId.put(record.dumpId(), instance.id().value());
                    created++;
                } catch (IllegalArgumentException exception) {
                    failedInserts.add("entity " + group.entityAzName() + " record " + record.dumpId() + ": " + exception.getMessage());
                }
            }
            createdEntityCounts.put(group.entityAzName(), created);
        }

        int createdLinks = 0;
        int skippedDuplicateLinks = 0;
        Set<String> createdLinkKeys = new HashSet<>();
        for (DumpAssociationLink link : dump.links()) {
            String sourceId = importedIdsByDumpId.get(link.sourceDumpId());
            String targetId = importedIdsByDumpId.get(link.targetDumpId());
            if (sourceId == null || targetId == null) {
                failedInserts.add("association " + link.associationAzName() + " link "
                        + link.sourceDumpId() + " -> " + link.targetDumpId() + ": referenced record was not imported");
                continue;
            }
            String linkKey = link.associationAzName() + "\n" + sourceId + "\n" + targetId;
            if (!createdLinkKeys.add(linkKey)) {
                skippedDuplicateLinks++;
                continue;
            }
            try {
                instanceService.createAssociationLink(targetModelAzName, root.instanceRootId(), link.associationAzName(), sourceId, targetId);
                createdLinks++;
            } catch (IllegalArgumentException exception) {
                failedInserts.add("association " + link.associationAzName() + " link "
                        + link.sourceDumpId() + " -> " + link.targetDumpId() + ": " + exception.getMessage());
            }
        }
        return new ModelInstanceDumpImportResult(
                root,
                createdEntityCounts,
                createdLinks,
                skippedDuplicateLinks,
                precheck.warnings(),
                failedInserts
        );
    }

    private static DumpEntityRecord toDumpRecord(VEntity entity, EntityInstance instance) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (VAttribute attribute : entity.attributes()) {
            InstanceValue value = instance.values().get(attribute.azName());
            values.put(attribute.azName(), value == null ? null : value.value());
        }
        return new DumpEntityRecord(instance.id().value(), values);
    }

    private static Map<String, Object> submittedValues(Map<String, Object> dumpValues) {
        LinkedHashMap<String, Object> submitted = new LinkedHashMap<>();
        dumpValues.forEach((key, value) -> {
            if (value != null) {
                submitted.put(key, value);
            }
        });
        return submitted;
    }

    private static boolean valueFitsType(DataType type, Object value) {
        return switch (type) {
            case TEXT, DATA, URL, DATE, TIME, DATETIME -> value instanceof String;
            case NUMERIC -> value instanceof Number || value instanceof String;
            case LOCATION -> value instanceof LocationValue || locationFitsType(value);
        };
    }

    private static boolean locationFitsType(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return false;
        }
        if (!values.containsKey("latitude") || !values.containsKey("longitude")) {
            return false;
        }
        Object latitude = values.get("latitude");
        Object longitude = values.get("longitude");
        if (!(latitude instanceof Number latitudeNumber) || !(longitude instanceof Number longitudeNumber)) {
            return false;
        }
        try {
            new LocationValue(latitudeNumber.doubleValue(), longitudeNumber.doubleValue());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean valueFitsValueSet(ModelRoot modelRoot, VAttribute attribute, Object value) {
        if (attribute.valueSetAzName() == null) {
            return true;
        }
        ValueSet valueSet = modelRoot.findValueSet(attribute.valueSetAzName())
                .orElseThrow(() -> new IllegalArgumentException("ValueSet not found: " + attribute.valueSetAzName()));
        if (valueSet.type() != attribute.type()) {
            return false;
        }
        try {
            return valueSet.containsTechnicalValue(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Map<String, VEntity> entitiesByKey(ModelRoot modelRoot) {
        LinkedHashMap<String, VEntity> entities = new LinkedHashMap<>();
        for (VEntity entity : modelRoot.entities()) {
            entities.put(VEntity.uniquenessKey(entity.azName()), entity);
        }
        return entities;
    }

    private static Map<String, VAttribute> attributesByKey(VEntity entity) {
        LinkedHashMap<String, VAttribute> attributes = new LinkedHashMap<>();
        for (VAttribute attribute : entity.attributes()) {
            attributes.put(VAttribute.uniquenessKey(attribute.azName()), attribute);
        }
        return attributes;
    }

    private static Map<String, Association> associationsByKey(ModelRoot modelRoot) {
        LinkedHashMap<String, Association> associations = new LinkedHashMap<>();
        for (Association association : modelRoot.associations()) {
            associations.put(Association.uniquenessKey(association.azName()), association);
        }
        return associations;
    }
}
