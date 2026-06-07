package org.vedenemo.web.api.http;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record WebApiConfig(String host, int port, Set<String> allowedOrigins) {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";

    public static WebApiConfig fromEnvironment(Map<String, String> environment) {
        return new WebApiConfig(
                valueOrDefault(environment.get("VEDENEMO_WEB_HOST"), DEFAULT_HOST),
                intOrDefault(environment.get("VEDENEMO_WEB_PORT"), DEFAULT_PORT),
                originsOrDefault(environment.get("VEDENEMO_ALLOWED_ORIGINS")));
    }

    public boolean allowsOrigin(String origin) {
        return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
    }

    public boolean allowsAnyOrigin() {
        return allowedOrigins.contains("*");
    }

    private static String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int intOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static Set<String> originsOrDefault(String value) {
        String origins = valueOrDefault(value, DEFAULT_ALLOWED_ORIGINS);
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
