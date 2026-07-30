package org.vedenemo.core.instance;

import org.vedenemo.core.model.ModelRoot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ModelInstanceRegistry {

    private final Map<String, ModelInstanceDataset> datasetsByModel = new LinkedHashMap<>();

    public synchronized ModelInstanceDataset datasetFor(ModelRoot modelRoot) {
        Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        return datasetsByModel.computeIfAbsent(
                ModelRoot.uniquenessKey(modelRoot.azName()),
                ignored -> new ModelInstanceDataset(modelRoot.azName(), modelRoot.version().toString())
        );
    }
}
