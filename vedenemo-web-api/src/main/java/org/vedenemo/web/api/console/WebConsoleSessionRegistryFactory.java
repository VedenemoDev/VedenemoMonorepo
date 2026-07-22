package org.vedenemo.web.api.console;

import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;

public final class WebConsoleSessionRegistryFactory {

    private WebConsoleSessionRegistryFactory() {
    }

    public static WebConsoleSessionRegistry create(
            SessionManager sessionManager,
            ModelRegistry modelRegistry,
            ModelChangeBroadcaster modelChangeBroadcaster
    ) {
        return new WebConsoleSessionRegistry(
                new InProcessConsoleSessionClient(sessionManager, modelRegistry),
                new InProcessConsoleModelClient(modelRegistry),
                new InProcessConsoleCommandClient(sessionManager, modelChangeBroadcaster)
        );
    }
}
