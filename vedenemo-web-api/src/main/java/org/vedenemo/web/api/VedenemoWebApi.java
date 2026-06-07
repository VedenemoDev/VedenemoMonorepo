package org.vedenemo.web.api;

import io.javalin.Javalin;
import org.vedenemo.web.api.http.CorsSupport;
import org.vedenemo.web.api.http.WebApiConfig;
import org.vedenemo.web.api.resource.ModelsResource;

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
        return Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.routes.before(context -> CorsSupport.apply(context, config));
            javalinConfig.routes.options("/*", context -> context.status(204));
            new ModelsResource().register(javalinConfig.routes);
        });
    }
}
