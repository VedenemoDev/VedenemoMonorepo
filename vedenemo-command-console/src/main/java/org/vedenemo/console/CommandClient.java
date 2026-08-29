package org.vedenemo.console;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface CommandClient {

    void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException, InterruptedException;

    void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType,
            boolean required,
            String valueSetAzName
    ) throws IOException, InterruptedException;

    default void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType
    ) throws IOException, InterruptedException {
        createAttribute(sessionId, entityAzName, attributeAzName, attributeVisName, dataType, false, null);
    }

    default void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType,
            String valueSetAzName
    ) throws IOException, InterruptedException {
        createAttribute(sessionId, entityAzName, attributeAzName, attributeVisName, dataType, false, valueSetAzName);
    }

    void createValueSet(
            UUID sessionId,
            String valueSetAzName,
            String dataType,
            List<ValueSetEntryInput> entries
    ) throws IOException, InterruptedException;

    void setAttributeValueSet(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String valueSetAzName
    ) throws IOException, InterruptedException;

    void createAssociation(
            UUID sessionId,
            String kind,
            String associationAzName,
            String associationVisName,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String sourceRoleName,
            String targetRoleName,
            String sourceCardinality,
            String targetCardinality
    ) throws IOException, InterruptedException;

    UndoCommandResult undo(UUID sessionId) throws IOException, InterruptedException;
}
