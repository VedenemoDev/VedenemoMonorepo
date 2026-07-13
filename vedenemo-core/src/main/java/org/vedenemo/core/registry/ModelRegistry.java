package org.vedenemo.core.registry;

import org.vedenemo.core.model.ModelRoot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelRegistry {

    private final Map<String, ModelRoot> modelsByAzName = new LinkedHashMap<>();

    public synchronized ModelRoot add(ModelRoot modelRoot) {
        Objects.requireNonNull(modelRoot, "modelRoot must not be null");
        String key = ModelRoot.uniquenessKey(modelRoot.azName());
        if (modelsByAzName.containsKey(key)) {
            throw new DuplicateModelRootException(modelRoot.azName());
        }
        modelsByAzName.put(key, modelRoot);
        return modelRoot;
    }

    public synchronized List<ModelRoot> list() {
        return List.copyOf(new ArrayList<>(modelsByAzName.values()));
    }

    public synchronized Optional<ModelRoot> find(String azName) {
        return Optional.ofNullable(modelsByAzName.get(ModelRoot.uniquenessKey(azName)));
    }

    public synchronized boolean contains(String azName) {
        return modelsByAzName.containsKey(ModelRoot.uniquenessKey(azName));
    }
}
