package org.vedenemo.storage.memory;

import org.vedenemo.core.model.VedenemoModel;
import org.vedenemo.core.spi.storage.ModelStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Initial in-memory ModelStorage adapter.
 *
 * This is intended for bootstrapping and tests, not durable persistence.
 */
public final class InMemoryModelStorage implements ModelStorage {

    private final Map<String, VedenemoModel> models = new HashMap<>();

    @Override
    public void save(String modelId, VedenemoModel model) {
        models.put(requireModelId(modelId), Objects.requireNonNull(model, "model must not be null"));
    }

    @Override
    public Optional<VedenemoModel> load(String modelId) {
        return Optional.ofNullable(models.get(requireModelId(modelId)));
    }

    private static String requireModelId(String modelId) {
        Objects.requireNonNull(modelId, "modelId must not be null");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        return modelId;
    }
}
