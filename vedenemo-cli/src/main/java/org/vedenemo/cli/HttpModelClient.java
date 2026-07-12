package org.vedenemo.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpModelClient implements ModelClient {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"version\":\"([^\"]*)\"}"
    );

    private final URI apiBaseUrl;
    private final HttpClient httpClient;

    public HttpModelClient(URI apiBaseUrl) {
        this(apiBaseUrl, HttpClient.newHttpClient());
    }

    HttpModelClient(URI apiBaseUrl, HttpClient httpClient) {
        this.apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public List<ModelSummary> listModels() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/list"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("model list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseModels(response.body());
    }

    @Override
    public ModelSummary addModel(String azName, String visName, String version) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/add"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"azName":"%s","visName":"%s","version":"%s"}\
                        """.formatted(escapeJson(azName), escapeJson(visName), escapeJson(version))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("model add failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        List<ModelSummary> models = parseModels("[" + response.body() + "]");
        if (models.size() != 1) {
            throw new IOException("model add response did not contain one model");
        }
        return models.getFirst();
    }

    private static List<ModelSummary> parseModels(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<ModelSummary> models = new ArrayList<>();
        Matcher matcher = MODEL_PATTERN.matcher(body);
        while (matcher.find()) {
            models.add(new ModelSummary(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        if (models.isEmpty()) {
            throw new IOException("model response did not contain parseable models");
        }
        return List.copyOf(models);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
