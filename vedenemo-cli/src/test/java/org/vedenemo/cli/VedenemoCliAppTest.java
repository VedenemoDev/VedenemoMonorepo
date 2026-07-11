package org.vedenemo.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VedenemoCliAppTest {

    @Test
    void startsSessionShowsPromptAndCleansUpOnExit() {
        UUID sessionId = UUID.randomUUID();
        TestSessionClient sessionClient = new TestSessionClient(sessionId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VedenemoCliApp app = new VedenemoCliApp(
                sessionClient,
                new ByteArrayInputStream("exit\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false
        );

        int exitCode = app.run();

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(text.contains("Session with UUID " + sessionId + " is created / attached to."));
        assertTrue(text.contains("VedenemoCli>"));
        assertEquals(sessionId, sessionClient.endedSessionId);
    }

    @Test
    void emptyLineEchoesEmptyLineAndContinuesPrompting() {
        UUID sessionId = UUID.randomUUID();
        TestSessionClient sessionClient = new TestSessionClient(sessionId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VedenemoCliApp app = new VedenemoCliApp(
                sessionClient,
                new ByteArrayInputStream("\nexit\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false
        );

        int exitCode = app.run();

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(text.contains("VedenemoCli>\nVedenemoCli>"));
        assertEquals(sessionId, sessionClient.endedSessionId);
    }

    private static final class TestSessionClient implements SessionClient {
        private final UUID sessionId;
        private UUID endedSessionId;

        private TestSessionClient(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public UUID startSession() {
            return sessionId;
        }

        @Override
        public void endSession(UUID sessionId) throws IOException {
            endedSessionId = sessionId;
        }
    }
}
