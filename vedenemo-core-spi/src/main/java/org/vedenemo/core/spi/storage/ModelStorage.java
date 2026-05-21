package org.vedenemo.core.spi.storage;

import org.vedenemo.core.model.VedenemoModel;

import java.util.Optional;

/**
 * Port for storing and loading Vedenemo models.
 *
 * Implementations belong in adapter modules, not in vedenemo-core.
 */
public interface ModelStorage {

    void save(String modelId, VedenemoModel model);

    Optional<VedenemoModel> load(String modelId);
}
