package org.vedenemo.web.api.resource;

import io.javalin.router.JavalinDefaultRoutingApi;

public final class ModelsResource {
    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/models/ping", context -> context
                .status(200)
                .contentType("application/json")
                .result("{\"status\":\"ok\"}"));
    }
}
