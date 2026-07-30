package org.vedenemo.core.instance;

import org.vedenemo.core.model.ModelRoot;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ModelInstanceRegistry {

    private final Map<String, LinkedHashMap<String, ModelInstanceDataset>> datasetsByModel = new LinkedHashMap<>();

    public synchronized ModelInstanceDataset createDataset(ModelRoot modelRoot, String visName) {
        Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        String instanceRootId = UUID.randomUUID().toString();
        ModelInstanceDataset dataset = new ModelInstanceDataset(
                instanceRootId,
                modelRoot.azName(),
                modelRoot.version().toString(),
                visName
        );
        datasetsForModel(modelRoot).put(instanceRootId, dataset);
        return dataset;
    }

    public synchronized Optional<ModelInstanceDataset> findDataset(ModelRoot modelRoot, String instanceRootId) {
        Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        Objects.requireNonNull(instanceRootId, "instanceRootId must not be null");
        return Optional.ofNullable(datasetsForModel(modelRoot).get(instanceRootId));
    }

    public synchronized List<ModelInstanceDataset> listDatasets(ModelRoot modelRoot) {
        Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        return List.copyOf(datasetsForModel(modelRoot).values());
    }

    private LinkedHashMap<String, ModelInstanceDataset> datasetsForModel(ModelRoot modelRoot) {
        return datasetsByModel.computeIfAbsent(ModelRoot.uniquenessKey(modelRoot.azName()), ignored -> new LinkedHashMap<>());
    }
}
