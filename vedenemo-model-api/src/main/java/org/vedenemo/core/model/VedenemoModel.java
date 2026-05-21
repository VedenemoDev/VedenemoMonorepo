package org.vedenemo.core.model;

import java.util.Objects;

/**
 * Minimal placeholder for the Vedenemo model.
 *
 * Real model structure is intentionally out of scope for the first milestone.
 */
public record VedenemoModel(String id) {

    public VedenemoModel {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
