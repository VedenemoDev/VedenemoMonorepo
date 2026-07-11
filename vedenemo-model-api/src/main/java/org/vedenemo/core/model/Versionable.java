package org.vedenemo.core.model;

import java.util.Objects;
import java.util.Optional;

public abstract class Versionable {

    private final ModelVersion activeSince;
    private final ModelVersion deprecatedSince;

    protected Versionable(ModelVersion activeSince, ModelVersion deprecatedSince) {
        this.activeSince = Objects.requireNonNull(activeSince, "activeSince must not be null");
        if (deprecatedSince != null && deprecatedSince.compareTo(activeSince) <= 0) {
            throw new IllegalArgumentException("deprecatedSince must be later than activeSince");
        }
        this.deprecatedSince = deprecatedSince;
    }

    public final ModelVersion activeSince() {
        return activeSince;
    }

    public final Optional<ModelVersion> deprecatedSince() {
        return Optional.ofNullable(deprecatedSince);
    }
}
