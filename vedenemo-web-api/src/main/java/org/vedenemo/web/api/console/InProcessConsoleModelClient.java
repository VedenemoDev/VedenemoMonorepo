package org.vedenemo.web.api.console;

import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

final class InProcessConsoleModelClient implements ModelClient {

    private final ModelRegistry modelRegistry;

    InProcessConsoleModelClient(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
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
    public String exportScript(String modelAzName) throws IOException {
        throw new IOException("model save is not supported by this console adapter");
    }

    @Override
    public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException {
        throw new IOException("model load is not supported by this console adapter");
    }
}
