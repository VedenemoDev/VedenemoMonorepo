package org.vedenemo.web.api.console;

import org.vedenemo.console.AssociationSummary;
import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelAlreadyExistsException;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SnapshotSummary;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.Association;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.script.VedenemoScriptImportResult;
import org.vedenemo.core.script.VedenemoScriptService;
import org.vedenemo.core.spi.snapshot.SnapshotContent;
import org.vedenemo.core.spi.snapshot.SnapshotDescriptor;
import org.vedenemo.core.spi.snapshot.SnapshotStore;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    InProcessConsoleModelClient(
            ModelRegistry modelRegistry,
            ModelCommandJournal commandJournal,
            ModelChangeBroadcaster modelChangeBroadcaster,
            Optional<SnapshotStore> snapshotStore,
            String snapshotScope,
            Clock clock
    ) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
        this.commandJournal = Objects.requireNonNull(commandJournal, "commandJournal must not be null");
        this.scriptService = new VedenemoScriptService(modelRegistry, commandJournal);
        this.modelChangeBroadcaster = Objects.requireNonNull(modelChangeBroadcaster, "modelChangeBroadcaster must not be null");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null");
        this.snapshotScope = requireText(snapshotScope, "snapshotScope");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
                        entity.deprecatedSince().map(Object::toString).orElse(null)
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
                        attribute.deprecatedSince().map(Object::toString).orElse(null)
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
                association.deprecatedSince().map(Object::toString).orElse(null)
        );
    }

    private SnapshotStore configuredSnapshotStore() throws IOException {
        return snapshotStore.orElseThrow(() -> new IOException("Cloud snapshot store is not configured."));
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
