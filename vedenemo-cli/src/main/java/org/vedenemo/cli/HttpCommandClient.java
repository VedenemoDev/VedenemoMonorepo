package org.vedenemo.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

public final class HttpCommandClient implements CommandClient {

    private final URI apiBaseUrl;
    private final HttpClient httpClient;

    public HttpCommandClient(URI apiBaseUrl) {
        this(apiBaseUrl, HttpClient.newHttpClient());
    }

    HttpCommandClient(URI apiBaseUrl, HttpClient httpClient) {
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public void createEntity(UUID sessionId, String entityAzName, String entityVisName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/create-entity"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"entityAzName":"%s","entityVisName":"%s"}\
                        """.formatted(escapeJson(entityAzName), escapeJson(entityVisName))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("entity add failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public UndoCommandResult undo(UUID sessionId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/undo"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return UndoCommandResult.UNDONE;
        }
        if (response.statusCode() == 304) {
            return UndoCommandResult.NOTHING_TO_UNDO;
        }
        throw new IOException("Unexpected response code: " + response.statusCode());
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
