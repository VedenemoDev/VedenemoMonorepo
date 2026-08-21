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

    default List<ModelInstanceRootSummary> listInstanceRoots(String modelAzName) throws IOException, InterruptedException {
        throw new IOException("model instance roots are not supported by this model client");
    }

    String exportScript(String modelAzName) throws IOException, InterruptedException;

    ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException, InterruptedException;

    List<SnapshotSummary> listSnapshots() throws IOException, InterruptedException;

    SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) throws IOException, InterruptedException;

    ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException, InterruptedException;

    default String exportDump(String modelAzName, String instanceRootId) throws IOException, InterruptedException {
        throw new IOException("model instance dumps are not supported by this model client");
    }

    default DumpPrecheckResult precheckDump(String modelAzName, String dumpContent) throws IOException, InterruptedException {
        throw new IOException("model instance dumps are not supported by this model client");
    }

    default DumpImportResult importDump(String modelAzName, String dumpContent, boolean confirmVersionMismatch) throws IOException, InterruptedException {
        throw new IOException("model instance dumps are not supported by this model client");
    }

    default List<DumpSummary> listDumps(String modelAzName) throws IOException, InterruptedException {
        throw new IOException("cloud dumps are not supported by this model client");
    }

    default DumpSummary saveDump(String modelAzName, String instanceRootId, String dumpName) throws IOException, InterruptedException {
        throw new IOException("cloud dumps are not supported by this model client");
    }

    default DumpPrecheckResult precheckStoredDump(String modelAzName, String dumpKey) throws IOException, InterruptedException {
        throw new IOException("cloud dumps are not supported by this model client");
    }

    default DumpImportResult loadStoredDump(String modelAzName, String dumpKey, boolean confirmVersionMismatch) throws IOException, InterruptedException {
        throw new IOException("cloud dumps are not supported by this model client");
    }
}
