package org.vedenemo.core.instance;

import java.util.Objects;
import java.util.UUID;

public record InstanceId(String value) {

    public InstanceId {
        Objects.requireNonNull(value, "value must not be null");
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("instance id must be a UUID string", exception);
        }
    }

    public static InstanceId random() {
        return new InstanceId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
