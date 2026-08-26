package org.vedenemo.cli;

import org.vedenemo.console.CommandClient;
import org.vedenemo.console.UndoCommandResult;
import org.vedenemo.console.ValueSetEntryInput;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpCommandClient implements CommandClient {

    private static final Pattern UNDO_RESPONSE_PATTERN = Pattern.compile(
            "\\{\"status\":\"undone\",\"undoneCommand\":\"([^\"]*)\",\"modelAzName\":\"([^\"]*)\",\"entityAzName\":(\"([^\"]*)\"|null),\"attributeAzName\":(\"([^\"]*)\"|null),\"associationAzName\":(\"([^\"]*)\"|null)}"
    );

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
    public void createAttribute(
            UUID sessionId,
            String entityAzName,
            String attributeAzName,
            String attributeVisName,
            String dataType,
            String valueSetAzName
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/create-attribute"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"entityAzName":"%s","attributeAzName":"%s","attributeVisName":"%s","dataType":"%s","valueSetAzName":%s}\
                        """.formatted(
                        escapeJson(entityAzName),
                        escapeJson(attributeAzName),
                        escapeJson(attributeVisName),
                        escapeJson(dataType),
                        jsonStringOrNull(valueSetAzName)
                )))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("attribute add failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void createValueSet(UUID sessionId, String valueSetAzName, String dataType, List<ValueSetEntryInput> entries)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/create-value-set"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"valueSetAzName":"%s","dataType":"%s","entries":[%s]}\
                        """.formatted(
                        escapeJson(valueSetAzName),
                        escapeJson(dataType),
                        valueSetEntriesJson(entries)
                )))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("value set add failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void setAttributeValueSet(UUID sessionId, String entityAzName, String attributeAzName, String valueSetAzName)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/set-attribute-value-set"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"entityAzName":"%s","attributeAzName":"%s","valueSetAzName":"%s"}\
                        """.formatted(
                        escapeJson(entityAzName),
                        escapeJson(attributeAzName),
                        escapeJson(valueSetAzName)
                )))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("attribute value set attach failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void createAssociation(
            UUID sessionId,
            String kind,
            String associationAzName,
            String associationVisName,
            String sourceEntityAzName,
            String targetEntityAzName,
            String cardinality,
            String sourceRoleName,
            String targetRoleName,
            String sourceCardinality,
            String targetCardinality
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/create-association"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"kind":"%s","associationAzName":"%s","associationVisName":"%s","sourceEntityAzName":"%s","targetEntityAzName":"%s","cardinality":"%s","sourceRoleName":%s,"targetRoleName":%s,"sourceCardinality":%s,"targetCardinality":%s}\
                        """.formatted(
                        escapeJson(kind),
                        escapeJson(associationAzName),
                        escapeJson(associationVisName),
                        escapeJson(sourceEntityAzName),
                        escapeJson(targetEntityAzName),
                        escapeJson(cardinality),
                        jsonStringOrNull(sourceRoleName),
                        jsonStringOrNull(targetRoleName),
                        jsonStringOrNull(sourceCardinality),
                        jsonStringOrNull(targetCardinality)
                )))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("association add failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public UndoCommandResult undo(UUID sessionId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/sessions/" + sessionId + "/commands/undo"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return parseUndoResponse(response.body());
        }
        if (response.statusCode() == 304) {
            return UndoCommandResult.NOTHING_TO_UNDO;
        }
        throw new IOException("Unexpected response code: " + response.statusCode());
    }

    private static UndoCommandResult parseUndoResponse(String body) throws IOException {
        Matcher matcher = UNDO_RESPONSE_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new IOException("undo response did not contain parseable undo details");
        }
        return UndoCommandResult.undone(matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(6), matcher.group(8));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private static String valueSetEntriesJson(List<ValueSetEntryInput> entries) {
        return entries.stream()
                .map(entry -> "{\"technicalValue\":%s,\"visName\":\"%s\"}".formatted(
                        jsonValue(entry.technicalValue()),
                        escapeJson(entry.visName())
                ))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String jsonValue(Object value) {
        if (value instanceof Number number) {
            return number.toString();
        }
        return jsonStringOrNull(Objects.toString(value, null));
    }
}
