package org.vedenemo.cli;

import java.io.IOException;
import java.util.UUID;

public interface CommandClient {

    void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException, InterruptedException;

    UndoCommandResult undo(UUID sessionId) throws IOException, InterruptedException;
}
