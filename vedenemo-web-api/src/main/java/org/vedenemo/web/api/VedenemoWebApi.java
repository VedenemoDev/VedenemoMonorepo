package org.vedenemo.web.api;

import io.javalin.Javalin;
import org.vedenemo.app.VedenemoApp;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.core.spi.snapshot.SnapshotStore;
import org.vedenemo.storage.gcs.GcsSnapshotStore;
import org.vedenemo.web.api.console.WebConsoleSessionRegistryFactory;
import org.vedenemo.web.api.events.ModelChangeBroadcaster;
import org.vedenemo.web.api.http.CorsSupport;
import org.vedenemo.web.api.http.WebApiConfig;
import org.vedenemo.web.api.resource.ConsoleResource;
import org.vedenemo.web.api.resource.ModelsResource;
import org.vedenemo.web.api.resource.SessionResource;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class VedenemoWebApi {
    private static final String DEFAULT_SNAPSHOT_SCOPE = "dev";

    private VedenemoWebApi() {
    }

    public static void main(String[] args) {
        WebApiConfig config = WebApiConfig.fromEnvironment(System.getenv());
        Javalin app = create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        app.start(config.host(), config.port());
    }

    public static Javalin create(WebApiConfig config) {
        ModelRegistry modelRegistry = VedenemoApp.createModelRegistry();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        return create(config, modelRegistry, VedenemoApp.createSessionManager(modelRegistry, commandJournal), commandJournal);
    }

    public static Javalin create(WebApiConfig config, ModelRegistry modelRegistry) {
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        return create(config, modelRegistry, VedenemoApp.createSessionManager(modelRegistry, commandJournal), commandJournal);
    }

    public static Javalin create(WebApiConfig config, ModelRegistry modelRegistry, SessionManager sessionManager) {
        return create(config, modelRegistry, sessionManager, sessionManager.commandJournal());
    }

    public static Javalin create(
            WebApiConfig config,
            ModelRegistry modelRegistry,
            SessionManager sessionManager,
            ModelCommandJournal commandJournal
    ) {
        return create(config, modelRegistry, sessionManager, commandJournal, snapshotStoreFromEnvironment(System.getenv()), snapshotScopeFromEnvironment(System.getenv()), Clock.systemUTC());
    }

    public static Javalin create(
            WebApiConfig config,
            ModelRegistry modelRegistry,
            SessionManager sessionManager,
            ModelCommandJournal commandJournal,
            Optional<SnapshotStore> snapshotStore,
            String snapshotScope,
            Clock clock
    ) {
        return Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.before(context -> CorsSupport.apply(context, config));
            javalinConfig.routes.options("/*", context -> context.status(204));
            ModelChangeBroadcaster modelChangeBroadcaster = new ModelChangeBroadcaster();
            modelChangeBroadcaster.register(javalinConfig.routes);
            new ModelsResource(modelRegistry, commandJournal, modelChangeBroadcaster).register(javalinConfig.routes);
            new SessionResource(sessionManager, modelRegistry, modelChangeBroadcaster).register(javalinConfig.routes);
            new ConsoleResource(WebConsoleSessionRegistryFactory.create(
                    sessionManager,
                    modelRegistry,
                    modelChangeBroadcaster,
                    snapshotStore,
                    snapshotScope,
                    clock
            )).register(javalinConfig.routes);
        });
    }

    private static Optional<SnapshotStore> snapshotStoreFromEnvironment(Map<String, String> environment) {
        String store = environment.getOrDefault("VEDENEMO_SNAPSHOT_STORE", "").trim().toLowerCase(Locale.ROOT);
        if (store.isBlank()) {
            return Optional.empty();
        }
        if (!"gcs".equals(store)) {
            throw new IllegalArgumentException("unsupported VEDENEMO_SNAPSHOT_STORE: " + store);
        }
        return Optional.of(new GcsSnapshotStore(
                requireEnvironment(environment, "VEDENEMO_GCS_PROJECT_ID"),
                requireEnvironment(environment, "VEDENEMO_GCS_BUCKET"),
                requireEnvironment(environment, "VEDENEMO_GCS_PREFIX")
        ));
    }

    private static String snapshotScopeFromEnvironment(Map<String, String> environment) {
        return environment.getOrDefault("VEDENEMO_SNAPSHOT_SCOPE", DEFAULT_SNAPSHOT_SCOPE).trim();
    }

    private static String requireEnvironment(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required when VEDENEMO_SNAPSHOT_STORE=gcs");
        }
        return value.trim();
    }
}
