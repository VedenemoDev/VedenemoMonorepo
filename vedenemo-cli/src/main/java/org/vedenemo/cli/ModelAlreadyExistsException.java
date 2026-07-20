package org.vedenemo.cli;

import java.io.IOException;

public final class ModelAlreadyExistsException extends IOException {
    public ModelAlreadyExistsException(String message) {
        super(message);
    }
}
