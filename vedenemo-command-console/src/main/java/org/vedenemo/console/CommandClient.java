package org.vedenemo.console;

import java.io.IOException;
import java.util.UUID;

public interface CommandClient {

    void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException, InterruptedException;

    void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType
    ) throws IOException, InterruptedException;

    void createAssociation(
            UUID sessionId,
            String kind,
            String associationAzName,
            String associationVisName,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality
    ) throws IOException, InterruptedException;

    UndoCommandResult undo(UUID sessionId) throws IOException, InterruptedException;
}
