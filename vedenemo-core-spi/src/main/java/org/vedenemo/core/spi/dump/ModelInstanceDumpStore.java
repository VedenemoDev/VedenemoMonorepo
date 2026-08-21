package org.vedenemo.core.spi.dump;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ModelInstanceDumpStore {

    List<ModelInstanceDumpDescriptor> listDumps(String scope, String modelAzName) throws IOException;

    Optional<ModelInstanceDumpContent> readDump(String scope, String dumpKey) throws IOException;

    ModelInstanceDumpDescriptor writeDump(
            String scope,
            String modelAzName,
            String dumpName,
            String content,
            ModelInstanceDumpDescriptor descriptor
    ) throws IOException;
}
