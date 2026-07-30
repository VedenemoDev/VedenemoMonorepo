package org.vedenemo.web.api.http;

import io.javalin.http.Context;

public final class CorsSupport {
    private CorsSupport() {
    }

    public static void apply(Context context, WebApiConfig config) {
        String origin = context.header("Origin");
        if (origin == null || origin.isBlank() || !config.allowsOrigin(origin)) {
            return;
        }

        context.header("Vary", "Origin");
        context.header("Access-Control-Allow-Origin", config.allowsAnyOrigin() ? "*" : origin);
        context.header("Access-Control-Allow-Methods", "DELETE, GET, OPTIONS, POST, PUT");
        context.header("Access-Control-Allow-Headers", "Accept, Content-Type");
        context.header("Access-Control-Max-Age", "3600");
    }
}
