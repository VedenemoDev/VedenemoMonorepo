package org.vedenemo.cli;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CliConfigTest {

    @Test
    void usesDefaultBackendBaseUrl() {
        CliConfig config = CliConfig.fromEnvironment(Map.of());

        assertEquals(URI.create("http://127.0.0.1:8080"), config.apiBaseUrl());
    }

    @Test
    void readsBackendBaseUrlFromEnvironmentAndRemovesTrailingSlash() {
        CliConfig config = CliConfig.fromEnvironment(Map.of("VEDENEMO_API_BASE_URL", "http://localhost:18080/"));

        assertEquals(URI.create("http://localhost:18080"), config.apiBaseUrl());
    }
}
