package org.vedenemo.cli;

import java.net.URI;
import java.util.Map;

public record CliConfig(URI apiBaseUrl) {

    private static final String API_BASE_URL_ENV = "VEDENEMO_API_BASE_URL";
    private static final String DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";

    public static CliConfig fromEnvironment(Map<String, String> environment) {
        String rawValue = environment.getOrDefault(API_BASE_URL_ENV, DEFAULT_API_BASE_URL);
        String normalized = rawValue.endsWith("/") ? rawValue.substring(0, rawValue.length() - 1) : rawValue;
        return new CliConfig(URI.create(normalized));
    }
}
