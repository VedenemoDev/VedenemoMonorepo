package org.vedenemo.web.api;

import io.javalin.Javalin;
import org.vedenemo.app.VedenemoApp;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.core.session.SessionManager;
import org.vedenemo.web.api.http.CorsSupport;
import org.vedenemo.web.api.http.WebApiConfig;
import org.vedenemo.web.api.resource.ModelsResource;
import org.vedenemo.web.api.resource.SessionResource;

public final class VedenemoWebApi {
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
        return Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.before(context -> CorsSupport.apply(context, config));
            javalinConfig.routes.options("/*", context -> context.status(204));
            new ModelsResource(modelRegistry, commandJournal).register(javalinConfig.routes);
            new SessionResource(sessionManager, modelRegistry).register(javalinConfig.routes);
        });
    }
}
