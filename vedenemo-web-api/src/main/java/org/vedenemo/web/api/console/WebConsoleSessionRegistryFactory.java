package org.vedenemo.web.api.console;

import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.core.spi.snapshot.SnapshotStore;
import org.vedenemo.console.ConsoleCapabilities;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

import java.time.Clock;
import java.util.Optional;

public final class WebConsoleSessionRegistryFactory {

    private WebConsoleSessionRegistryFactory() {
    }

    public static WebConsoleSessionRegistry create(
            SessionManager sessionManager,
            ModelRegistry modelRegistry,
            ModelChangeBroadcaster modelChangeBroadcaster,
            Optional<SnapshotStore> snapshotStore,
            String snapshotScope,
            Clock clock
    ) {
        return new WebConsoleSessionRegistry(
                new InProcessConsoleSessionClient(sessionManager, modelRegistry),
                new InProcessConsoleModelClient(
                        modelRegistry,
                        sessionManager.commandJournal(),
                        modelChangeBroadcaster,
                        snapshotStore,
                        snapshotScope,
                        clock
                ),
                new InProcessConsoleCommandClient(sessionManager, modelChangeBroadcaster),
                ConsoleCapabilities.webConsoleWithCloudSnapshots()
        );
    }
}
