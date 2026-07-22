package org.vedenemo.cli;

import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelSummary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpModelClient implements ModelClient {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"version\":\"([^\"]*)\"}"
    );
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"activeSince\":\"([^\"]*)\",\"deprecatedSince\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"dataType\":\"([^\"]*)\",\"activeSince\":\"([^\"]*)\",\"deprecatedSince\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "\\{\"modelAzName\":\"([^\"]*)\",\"commandCount\":([0-9]+)}"
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

    @Override
    public List<EntitySummary> listEntities(String modelAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/" + encodePath(modelAzName) + "/entities"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("entity list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseEntities(response.body());
    }

    @Override
    public List<AttributeSummary> listAttributes(String modelAzName, String entityAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve(
                        "/models/" + encodePath(modelAzName) + "/entities/" + encodePath(entityAzName) + "/attributes"
                ))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("attribute list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseAttributes(response.body());
    }

    @Override
    public String exportScript(String modelAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/" + encodePath(modelAzName) + "/script"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("model save failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    @Override
    public ModelImportResult importScript(String script, String modelAzNameOverride) throws IOException, InterruptedException {
        String path = "/models/script";
        if (modelAzNameOverride != null && !modelAzNameOverride.isBlank()) {
            path += "?modelAzName=" + encodePath(modelAzNameOverride.trim());
        }
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve(path))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(script, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 201) {
            return parseImportResult(response.body());
        }
        if (response.statusCode() == 409) {
            throw new ModelAlreadyExistsException("model load failed with HTTP status 409: " + response.body());
        }
        throw new IOException("model load failed with HTTP status " + response.statusCode() + ": " + response.body());
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

    private static List<EntitySummary> parseEntities(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<EntitySummary> entities = new ArrayList<>();
        Matcher matcher = ENTITY_PATTERN.matcher(body);
        while (matcher.find()) {
            entities.add(new EntitySummary(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(5)));
        }
        if (entities.isEmpty()) {
            throw new IOException("entity response did not contain parseable entities");
        }
        return List.copyOf(entities);
    }

    private static List<AttributeSummary> parseAttributes(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<AttributeSummary> attributes = new ArrayList<>();
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(body);
        while (matcher.find()) {
            attributes.add(new AttributeSummary(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4),
                    matcher.group(6)
            ));
        }
        if (attributes.isEmpty()) {
            throw new IOException("attribute response did not contain parseable attributes");
        }
        return List.copyOf(attributes);
    }

    private static ModelImportResult parseImportResult(String body) throws IOException {
        Matcher matcher = IMPORT_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new IOException("model load response did not contain import details");
        }
        return new ModelImportResult(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encodePath(String value) {
        return value.replace(" ", "%20");
    }
}
