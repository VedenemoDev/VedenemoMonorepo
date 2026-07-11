package org.vedenemo.cli;

import java.io.IOException;
import java.util.UUID;

public interface SessionClient {

    UUID startSession() throws IOException, InterruptedException;

    void endSession(UUID sessionId) throws IOException, InterruptedException;
}
