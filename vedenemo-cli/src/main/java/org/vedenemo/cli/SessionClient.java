package org.vedenemo.cli;

import java.io.IOException;
import java.util.UUID;

public interface SessionClient {

    UUID startSession() throws IOException, InterruptedException;

    void endSession(UUID sessionId) throws IOException, InterruptedException;

    void selectModel(UUID sessionId, String azName) throws IOException, InterruptedException;

    void clearSelectedModel(UUID sessionId) throws IOException, InterruptedException;
}
