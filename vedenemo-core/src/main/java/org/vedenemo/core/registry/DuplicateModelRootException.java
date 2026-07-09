package org.vedenemo.core.registry;

public final class DuplicateModelRootException extends RuntimeException {

    public DuplicateModelRootException(String azName) {
        super("model root already exists for azName: " + azName);
    }
}
