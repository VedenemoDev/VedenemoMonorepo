package org.vedenemo.web.api.console;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vedenemo.console.AssociationSummary;
import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.DumpImportResult;
import org.vedenemo.console.DumpPrecheckResult;
import org.vedenemo.console.DumpSummary;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelAlreadyExistsException;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelInstanceRootSummary;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SnapshotSummary;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.instance.ModelInstanceRoot;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.instance.dump.DumpAssociationLink;
import org.vedenemo.core.instance.dump.DumpEntityGroup;
import org.vedenemo.core.instance.dump.DumpEntityRecord;
import org.vedenemo.core.instance.dump.DumpModel;
import org.vedenemo.core.instance.dump.DumpRoot;
import org.vedenemo.core.instance.dump.ModelInstanceDump;
import org.vedenemo.core.instance.dump.ModelInstanceDumpImportResult;
import org.vedenemo.core.instance.dump.ModelInstanceDumpPrecheckResult;
import org.vedenemo.core.instance.dump.ModelInstanceDumpService;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.script.VedenemoScriptImportResult;
import org.vedenemo.core.script.VedenemoScriptService;
import org.vedenemo.core.spi.dump.ModelInstanceDumpContent;
import org.vedenemo.core.spi.dump.ModelInstanceDumpDescriptor;
import org.vedenemo.core.spi.dump.ModelInstanceDumpStore;
import org.vedenemo.core.spi.snapshot.SnapshotContent;
import org.vedenemo.core.spi.snapshot.SnapshotDescriptor;
import org.vedenemo.core.spi.snapshot.SnapshotStore;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class InProcessConsoleModelClient implements ModelClient {

    private final ModelRegistry modelRegistry;
    private final VedenemoScriptService scriptService;
    private final ModelCommandJournal commandJournal;
    private final ModelChangeBroadcaster modelChangeBroadcaster;
    private final Optional<SnapshotStore> snapshotStore;
    private final String snapshotScope;
    private final Optional<ModelInstanceDumpStore> dumpStore;
    private final String dumpScope;
    private final ModelInstanceService instanceService;
    private final ModelInstanceDumpService dumpService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    InProcessConsoleModelClient(
            ModelRegistry modelRegistry,
            ModelCommandJournal commandJournal,
            ModelChangeBroadcaster modelChangeBroadcaster,
            Optional<SnapshotStore> snapshotStore,
            String snapshotScope,
            Optional<ModelInstanceDumpStore> dumpStore,
            String dumpScope,
            ModelInstanceService instanceService,
            Clock clock
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.commandJournal = Objects.requireNonNull(commandJournal, "commandJournal must not be null");
        this.scriptService = new VedenemoScriptService(modelRegistry, commandJournal);
        this.modelChangeBroadcaster = Objects.requireNonNull(modelChangeBroadcaster, "modelChangeBroadcaster must not be null");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null");
        this.snapshotScope = requireText(snapshotScope, "snapshotScope");
        this.dumpStore = Objects.requireNonNull(dumpStore, "dumpStore must not be null");
        this.dumpScope = requireText(dumpScope, "dumpScope");
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
        this.dumpService = new ModelInstanceDumpService(instanceService);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.objectMapper = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }

    @Override
    public void ping() {
    }

    @Override
    public List<ModelSummary> listModels() {
        return modelRegistry.list().stream()
                .map(model -> new ModelSummary(model.azName(), model.visName(), model.version().toString()))
                .toList();
    }

    @Override
    public ModelSummary addModel(String azName, String visName, String version) throws IOException {
        throw new IOException("model add is not supported by this console adapter");
    }

    @Override
    public List<EntitySummary> listEntities(String modelAzName) throws IOException {
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IOException("model not found"));
        return modelRoot.entities().stream()
                .map(entity -> new EntitySummary(
                        entity.azName(),
                        entity.visName(),
                        entity.activeSince().toString(),
                        entity.deprecatedSince().map(Object::toString).orElse(null),
                        entity.retiredSince().map(Object::toString).orElse(null)
                ))
                .toList();
    }

    @Override
    public List<AttributeSummary> listAttributes(String modelAzName, String entityAzName) throws IOException {
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IOException("model not found"));
        VEntity entity = modelRoot.entities().stream()
                .filter(candidate -> VEntity.uniquenessKey(candidate.azName()).equals(VEntity.uniquenessKey(entityAzName)))
                .findFirst()
                .orElseThrow(() -> new IOException("entity not found"));
        return entity.attributes().stream()
                .map(attribute -> new AttributeSummary(
                        attribute.azName(),
                        attribute.visName(),
                        attribute.type().name(),
                        attribute.activeSince().toString(),
                        attribute.deprecatedSince().map(Object::toString).orElse(null),
                        attribute.retiredSince().map(Object::toString).orElse(null)
                ))
                .toList();
    }

    @Override
    public List<AssociationSummary> listAssociations(String modelAzName) throws IOException {
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IOException("model not found"));
        return modelRoot.associations().stream()
                .map(InProcessConsoleModelClient::toAssociationSummary)
                .toList();
    }

    @Override
    public List<AssociationSummary> listAssociations(String modelAzName, String entityAzName) throws IOException {
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IOException("model not found"));
        String entityKey = VEntity.uniquenessKey(entityAzName);
        boolean entityExists = modelRoot.entities().stream()
                .anyMatch(entity -> VEntity.uniquenessKey(entity.azName()).equals(entityKey));
        if (!entityExists) {
            throw new IOException("entity not found");
        }
        return modelRoot.associations().stream()
                .filter(association -> VEntity.uniquenessKey(association.sourceEntityAzName()).equals(entityKey)
                        || VEntity.uniquenessKey(association.targetEntityAzName()).equals(entityKey))
                .map(InProcessConsoleModelClient::toAssociationSummary)
                .toList();
    }

    @Override
    public List<ModelInstanceRootSummary> listInstanceRoots(String modelAzName) {
        return instanceService.listRoots(modelAzName).stream()
                .map(InProcessConsoleModelClient::toRootSummary)
                .toList();
    }

    @Override
    public String exportScript(String modelAzName) throws IOException {
        throw new IOException("model save is not supported by this console adapter");
    }

    @Override
    public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException {
        throw new IOException("model load is not supported by this console adapter");
    }

    @Override
    public List<SnapshotSummary> listSnapshots() throws IOException {
        SnapshotStore store = configuredSnapshotStore();
        return store.listSnapshots(snapshotScope).stream()
                .map(InProcessConsoleModelClient::toSnapshotSummary)
                .toList();
    }

    @Override
    public SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) throws IOException {
        SnapshotStore store = configuredSnapshotStore();
        ModelRoot modelRoot = modelRegistry.find(modelAzName)
                .orElseThrow(() -> new IOException("model not found"));
        String script = scriptService.exportModel(modelRoot.azName());
        SnapshotDescriptor descriptor = new SnapshotDescriptor(
                modelRoot.azName() + "/" + snapshotName + ".vdos",
                modelRoot.azName(),
                modelRoot.visName(),
                modelRoot.version().toString(),
                commandJournal.listForModel(modelRoot.azName()).size(),
                Instant.now(clock)
        );
        return toSnapshotSummary(store.writeSnapshot(snapshotScope, modelRoot.azName(), snapshotName, script, descriptor));
    }

    @Override
    public ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException {
        SnapshotStore store = configuredSnapshotStore();
        SnapshotContent snapshot = store.readSnapshot(snapshotScope, snapshotKey)
                .orElseThrow(() -> new IOException("cloud snapshot not found: " + snapshotKey));
        try {
            VedenemoScriptImportResult result = scriptService.importModel(snapshot.content(), modelAzNameOverride);
            modelChangeBroadcaster.broadcastModelChanged(result.modelAzName());
            return new ModelImportResult(result.modelAzName(), result.commandCount());
        } catch (IllegalStateException exception) {
            throw new ModelAlreadyExistsException("model load failed: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new IOException("model load failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public String exportDump(String modelAzName, String instanceRootId) throws IOException {
        try {
            return objectMapper.writeValueAsString(DumpJson.from(dumpService.exportDump(modelAzName, instanceRootId, Instant.now(clock))));
        } catch (JsonProcessingException exception) {
            throw new IOException("dump export failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public DumpPrecheckResult precheckDump(String modelAzName, String dumpContent) throws IOException {
        return toConsolePrecheck(dumpService.precheck(modelAzName, parseDump(dumpContent)));
    }

    @Override
    public DumpImportResult importDump(String modelAzName, String dumpContent, boolean confirmVersionMismatch) throws IOException {
        try {
            return toConsoleImportResult(dumpService.importDump(modelAzName, parseDump(dumpContent), confirmVersionMismatch));
        } catch (IllegalArgumentException exception) {
            throw new IOException("dump load failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public List<DumpSummary> listDumps(String modelAzName) throws IOException {
        ModelInstanceDumpStore store = configuredDumpStore();
        return store.listDumps(dumpScope, modelAzName).stream()
                .map(InProcessConsoleModelClient::toDumpSummary)
                .toList();
    }

    @Override
    public DumpSummary saveDump(String modelAzName, String instanceRootId, String dumpName) throws IOException {
        ModelInstanceDumpStore store = configuredDumpStore();
        ModelInstanceDump dump = dumpService.exportDump(modelAzName, instanceRootId, Instant.now(clock));
        try {
            String content = objectMapper.writeValueAsString(DumpJson.from(dump));
            return toDumpSummary(store.writeDump(dumpScope, modelAzName, dumpName, content, descriptorFor(dumpName, dump)));
        } catch (JsonProcessingException exception) {
            throw new IOException("dump save failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public DumpPrecheckResult precheckStoredDump(String modelAzName, String dumpKey) throws IOException {
        return toConsolePrecheck(dumpService.precheck(modelAzName, readStoredDump(dumpKey)));
    }

    @Override
    public DumpImportResult loadStoredDump(String modelAzName, String dumpKey, boolean confirmVersionMismatch) throws IOException {
        try {
            return toConsoleImportResult(dumpService.importDump(modelAzName, readStoredDump(dumpKey), confirmVersionMismatch));
        } catch (IllegalArgumentException exception) {
            throw new IOException("dump load failed: " + exception.getMessage(), exception);
        }
    }

    private static AssociationSummary toAssociationSummary(Association association) {
        return new AssociationSummary(
                association.azName(),
                association.visName(),
                association.kind().name(),
                association.sourceEntityAzName(),
                association.targetEntityAzName(),
                association.cardinality().toString(),
                association.sourceRoleName(),
                association.targetRoleName(),
                association.sourceCardinality() == null ? null : association.sourceCardinality().toString(),
                association.targetCardinality() == null ? null : association.targetCardinality().toString(),
                association.activeSince().toString(),
                association.deprecatedSince().map(Object::toString).orElse(null),
                association.retiredSince().map(Object::toString).orElse(null)
        );
    }

    private SnapshotStore configuredSnapshotStore() throws IOException {
        return snapshotStore.orElseThrow(() -> new IOException("Cloud snapshot store is not configured."));
    }

    private ModelInstanceDumpStore configuredDumpStore() throws IOException {
        return dumpStore.orElseThrow(() -> new IOException("Cloud dump store is not configured."));
    }

    private ModelInstanceDump parseDump(String dumpContent) throws IOException {
        try {
            return objectMapper.readValue(dumpContent, DumpJson.class).toCore();
        } catch (JsonProcessingException exception) {
            throw new IOException("dump parse failed: " + exception.getMessage(), exception);
        }
    }

    private ModelInstanceDump readStoredDump(String dumpKey) throws IOException {
        ModelInstanceDumpContent content = configuredDumpStore().readDump(dumpScope, dumpKey)
                .orElseThrow(() -> new IOException("cloud dump not found: " + dumpKey));
        return parseDump(content.content());
    }

    private static SnapshotSummary toSnapshotSummary(SnapshotDescriptor descriptor) {
        return new SnapshotSummary(
                descriptor.key(),
                descriptor.modelAzName(),
                descriptor.modelVisName(),
                descriptor.modelVersion(),
                descriptor.commandCount(),
                descriptor.savedAt().toString()
        );
    }

    private static ModelInstanceRootSummary toRootSummary(ModelInstanceRoot root) {
        return new ModelInstanceRootSummary(root.instanceRootId(), root.modelAzName(), root.modelVersion(), root.visName());
    }

    private static DumpSummary toDumpSummary(ModelInstanceDumpDescriptor descriptor) {
        return new DumpSummary(
                descriptor.key(),
                descriptor.modelAzName(),
                descriptor.modelVisName(),
                descriptor.modelVersion(),
                descriptor.rootVisName(),
                descriptor.entityRecordCount(),
                descriptor.associationLinkCount(),
                descriptor.savedAt().toString()
        );
    }

    private static DumpPrecheckResult toConsolePrecheck(ModelInstanceDumpPrecheckResult result) {
        return new DumpPrecheckResult(
                result.importable(),
                result.confirmationRequired(),
                result.warnings(),
                result.diagnostics()
        );
    }

    private static DumpImportResult toConsoleImportResult(ModelInstanceDumpImportResult result) {
        return new DumpImportResult(
                toRootSummary(result.root()),
                result.createdEntityCounts(),
                result.createdAssociationLinkCount(),
                result.skippedDuplicateLinkCount(),
                result.warnings(),
                result.failedInserts()
        );
    }

    private static ModelInstanceDumpDescriptor descriptorFor(String dumpName, ModelInstanceDump dump) {
        return new ModelInstanceDumpDescriptor(
                dump.model().azName() + "/" + dumpName + ".vdmp",
                dump.model().azName(),
                dump.model().visName(),
                dump.model().version(),
                dump.root().visName(),
                dump.entities().stream().mapToInt(group -> group.records().size()).sum(),
                dump.links().size(),
                dump.savedAt()
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private record DumpJson(
            String format,
            int formatVersion,
            String savedAt,
            DumpModel model,
            DumpRoot root,
            List<DumpEntityGroup> entities,
            List<DumpAssociationLink> links
    ) {

        private static DumpJson from(ModelInstanceDump dump) {
            return new DumpJson(
                    dump.format(),
                    dump.formatVersion(),
                    dump.savedAt().toString(),
                    dump.model(),
                    dump.root(),
                    dump.entities(),
                    dump.links()
            );
        }

        private ModelInstanceDump toCore() {
            return new ModelInstanceDump(
                    format,
                    formatVersion,
                    Instant.parse(savedAt),
                    model,
                    root,
                    entities,
                    links
            );
        }
    }
}
