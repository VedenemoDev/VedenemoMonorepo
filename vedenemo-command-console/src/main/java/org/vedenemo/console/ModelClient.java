package org.vedenemo.console;

import java.io.IOException;
import java.util.List;

public interface ModelClient {

    void ping() throws IOException, InterruptedException;

    List<ModelSummary> listModels() throws IOException, InterruptedException;

    ModelSummary addModel(String azName, String visName, String version) throws IOException, InterruptedException;

    List<EntitySummary> listEntities(String modelAzName) throws IOException, InterruptedException;

    List<AttributeSummary> listAttributes(String modelAzName, String entityAzName) throws IOException, InterruptedException;

    List<AssociationSummary> listAssociations(String modelAzName) throws IOException, InterruptedException;

    List<AssociationSummary> listAssociations(String modelAzName, String entityAzName) throws IOException, InterruptedException;

    String exportScript(String modelAzName) throws IOException, InterruptedException;

    ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException, InterruptedException;

    List<SnapshotSummary> listSnapshots() throws IOException, InterruptedException;

    SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) throws IOException, InterruptedException;

    ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException, InterruptedException;
}
