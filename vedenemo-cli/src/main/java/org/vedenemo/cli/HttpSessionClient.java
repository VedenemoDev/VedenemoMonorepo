package org.vedenemo.cli;

import org.vedenemo.console.SessionClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpSessionClient implements SessionClient {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\\{\"sessionId\":\"([^\"]+)\"}");

    private final URI apiBaseUrl;
    private final HttpClient httpClient;

    public HttpSessionClient(URI apiBaseUrl) {
        this(apiBaseUrl, HttpClient.newHttpClient());
    }

    HttpSessionClient(URI apiBaseUrl, HttpClient httpClient) {
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public UUID startSession() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("session start failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseSessionId(response.body());
    }

    @Override
    public void endSession(UUID sessionId) throws IOException, InterruptedException {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId))
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204 && response.statusCode() != 404) {
            throw new IOException("session cleanup failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void selectModel(UUID sessionId, String azName) throws IOException, InterruptedException {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(azName, "azName must not be null");
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/selected-model"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"azName\":\"" + escapeJson(azName) + "\"}"))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("model attach failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void clearSelectedModel(UUID sessionId) throws IOException, InterruptedException {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/selected-model"))
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("model detach failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    private static UUID parseSessionId(String body) throws IOException {
        Matcher matcher = SESSION_ID_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new IOException("session start response did not contain sessionId");
        }
        return UUID.fromString(matcher.group(1));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
