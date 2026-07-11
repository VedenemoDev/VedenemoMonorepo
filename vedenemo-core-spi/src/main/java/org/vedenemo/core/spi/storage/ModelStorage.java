package org.vedenemo.core.spi.storage;

import org.vedenemo.core.model.ModelRoot;

import java.util.Optional;

/**
 * Port for storing and loading Vedenemo models.
 *
 * Implementations belong in adapter modules, not in vedenemo-core.
 */
public interface ModelStorage {

    void save(String modelId, ModelRoot model);

    Optional<ModelRoot> load(String modelId);
}
