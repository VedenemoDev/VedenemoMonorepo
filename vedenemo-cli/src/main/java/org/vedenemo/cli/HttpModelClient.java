package org.vedenemo.cli;

import org.vedenemo.console.AssociationSummary;
import org.vedenemo.console.AttributeSummary;
import org.vedenemo.console.DumpImportResult;
import org.vedenemo.console.DumpPrecheckResult;
import org.vedenemo.console.DumpSummary;
import org.vedenemo.console.EntitySummary;
import org.vedenemo.console.ModelClient;
import org.vedenemo.console.ModelImportResult;
import org.vedenemo.console.ModelInstanceRootSummary;
import org.vedenemo.console.ModelSummary;
import org.vedenemo.console.SnapshotSummary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpModelClient implements ModelClient {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"version\":\"([^\"]*)\"}"
    );
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"activeSince\":\"([^\"]*)\",\"deprecatedSince\":(\"([^\"]*)\"|null),\"retiredSince\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"dataType\":\"([^\"]*)\",\"required\":(true|false),\"valueSetAzName\":(\"([^\"]*)\"|null),\"activeSince\":\"([^\"]*)\",\"deprecatedSince\":(\"([^\"]*)\"|null),\"retiredSince\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern ASSOCIATION_PATTERN = Pattern.compile(
            "\\{\"azName\":\"([^\"]*)\",\"visName\":\"([^\"]*)\",\"kind\":\"([^\"]*)\",\"sourceEntityAzName\":\"([^\"]*)\",\"targetEntityAzName\":\"([^\"]*)\",\"cardinality\":\"([^\"]*)\",\"sourceRoleName\":(\"([^\"]*)\"|null),\"targetRoleName\":(\"([^\"]*)\"|null),\"sourceCardinality\":(\"([^\"]*)\"|null),\"targetCardinality\":(\"([^\"]*)\"|null),\"activeSince\":\"([^\"]*)\",\"deprecatedSince\":(\"([^\"]*)\"|null),\"retiredSince\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "\\{\"modelAzName\":\"([^\"]*)\",\"commandCount\":([0-9]+)}"
    );
    private static final Pattern ROOT_PATTERN = Pattern.compile(
            "\\{\"instanceRootId\":\"([^\"]*)\",\"modelAzName\":\"([^\"]*)\",\"modelVersion\":\"([^\"]*)\",\"visName\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern DUMP_SUMMARY_PATTERN = Pattern.compile(
            "\\{\"key\":\"([^\"]*)\",\"modelAzName\":\"([^\"]*)\",\"modelVisName\":\"([^\"]*)\",\"modelVersion\":\"([^\"]*)\",\"rootVisName\":(\"([^\"]*)\"|null),\"entityRecordCount\":([0-9]+),\"associationLinkCount\":([0-9]+),\"savedAt\":\"([^\"]*)\"}"
    );
    private static final Pattern PRECHECK_PATTERN = Pattern.compile(
            "\\{\"importable\":(true|false),\"confirmationRequired\":(true|false),\"warnings\":\\[(.*?)]\\,\"diagnostics\":\\[(.*?)]}"
    );
    private static final Pattern IMPORT_ROOT_PATTERN = Pattern.compile(
            "\"root\":\\{\"instanceRootId\":\"([^\"]*)\",\"modelAzName\":\"([^\"]*)\",\"modelVersion\":\"([^\"]*)\",\"visName\":(\"([^\"]*)\"|null)}"
    );
    private static final Pattern CREATED_LINKS_PATTERN = Pattern.compile("\"createdAssociationLinkCount\":([0-9]+)");
    private static final Pattern SKIPPED_LINKS_PATTERN = Pattern.compile("\"skippedDuplicateLinkCount\":([0-9]+)");

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
    public void ping() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/ping"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("backend ping failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
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
    public List<AssociationSummary> listAssociations(String modelAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/models/" + encodePath(modelAzName) + "/associations"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("association list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseAssociations(response.body());
    }

    @Override
    public List<AssociationSummary> listAssociations(String modelAzName, String entityAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve(
                        "/models/" + encodePath(modelAzName) + "/entities/" + encodePath(entityAzName) + "/associations"
                ))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("association list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseAssociations(response.body());
    }

    @Override
    public List<ModelInstanceRootSummary> listInstanceRoots(String modelAzName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/data/" + encodePath(modelAzName) + "/roots"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("model instance root list failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseRoots(response.body());
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

    @Override
    public List<SnapshotSummary> listSnapshots() throws IOException {
        throw new IOException("cloud snapshots are not supported by the terminal HTTP model client");
    }

    @Override
    public SnapshotSummary saveSnapshot(String modelAzName, String snapshotName) throws IOException {
        throw new IOException("cloud snapshots are not supported by the terminal HTTP model client");
    }

    @Override
    public ModelImportResult loadSnapshot(String snapshotKey, String modelAzNameOverride) throws IOException {
        throw new IOException("cloud snapshots are not supported by the terminal HTTP model client");
    }

    @Override
    public String exportDump(String modelAzName, String instanceRootId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve(
                        "/data/" + encodePath(modelAzName) + "/roots/" + encodePath(instanceRootId) + "/dump"
                ))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("dump save failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    @Override
    public DumpPrecheckResult precheckDump(String modelAzName, String dumpContent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/data/" + encodePath(modelAzName) + "/dumps/_precheck"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(dumpContent, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("dump precheck failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parsePrecheck(response.body());
    }

    @Override
    public DumpImportResult importDump(String modelAzName, String dumpContent, boolean confirmVersionMismatch) throws IOException, InterruptedException {
        String body = "{\"dump\":" + dumpContent + ",\"confirmVersionMismatch\":" + confirmVersionMismatch + "}";
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("/data/" + encodePath(modelAzName) + "/dumps"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 201) {
            throw new IOException("dump load failed with HTTP status " + response.statusCode() + ": " + response.body());
        }
        return parseDumpImport(response.body());
    }

    @Override
    public List<DumpSummary> listDumps(String modelAzName) throws IOException {
        throw new IOException("cloud dumps are not supported by the terminal HTTP model client");
    }

    @Override
    public DumpSummary saveDump(String modelAzName, String instanceRootId, String dumpName) throws IOException {
        throw new IOException("cloud dumps are not supported by the terminal HTTP model client");
    }

    @Override
    public DumpPrecheckResult precheckStoredDump(String modelAzName, String dumpKey) throws IOException {
        throw new IOException("cloud dumps are not supported by the terminal HTTP model client");
    }

    @Override
    public DumpImportResult loadStoredDump(String modelAzName, String dumpKey, boolean confirmVersionMismatch) throws IOException {
        throw new IOException("cloud dumps are not supported by the terminal HTTP model client");
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
            entities.add(new EntitySummary(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(5), matcher.group(7)));
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
                    Boolean.parseBoolean(matcher.group(4)),
                    matcher.group(6),
                    matcher.group(7),
                    matcher.group(9),
                    matcher.group(11)
            ));
        }
        if (attributes.isEmpty()) {
            throw new IOException("attribute response did not contain parseable attributes");
        }
        return List.copyOf(attributes);
    }

    private static List<AssociationSummary> parseAssociations(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<AssociationSummary> associations = new ArrayList<>();
        Matcher matcher = ASSOCIATION_PATTERN.matcher(body);
        while (matcher.find()) {
            associations.add(new AssociationSummary(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4),
                    matcher.group(5),
                    matcher.group(6),
                    matcher.group(8),
                    matcher.group(10),
                    matcher.group(12),
                    matcher.group(14),
                    matcher.group(15),
                    matcher.group(17),
                    matcher.group(19)
            ));
        }
        if (associations.isEmpty()) {
            throw new IOException("association response did not contain parseable associations");
        }
        return List.copyOf(associations);
    }

    private static ModelImportResult parseImportResult(String body) throws IOException {
        Matcher matcher = IMPORT_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new IOException("model load response did not contain import details");
        }
        return new ModelImportResult(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    private static List<ModelInstanceRootSummary> parseRoots(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<ModelInstanceRootSummary> roots = new ArrayList<>();
        Matcher matcher = ROOT_PATTERN.matcher(body);
        while (matcher.find()) {
            roots.add(new ModelInstanceRootSummary(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(5)));
        }
        if (roots.isEmpty()) {
            throw new IOException("model instance root response did not contain parseable roots");
        }
        return List.copyOf(roots);
    }

    static List<DumpSummary> parseDumps(String body) throws IOException {
        if ("[]".equals(body)) {
            return List.of();
        }
        ArrayList<DumpSummary> dumps = new ArrayList<>();
        Matcher matcher = DUMP_SUMMARY_PATTERN.matcher(body);
        while (matcher.find()) {
            dumps.add(new DumpSummary(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4),
                    matcher.group(6),
                    Integer.parseInt(matcher.group(7)),
                    Integer.parseInt(matcher.group(8)),
                    matcher.group(9)
            ));
        }
        if (dumps.isEmpty()) {
            throw new IOException("dump response did not contain parseable dumps");
        }
        return List.copyOf(dumps);
    }

    private static DumpPrecheckResult parsePrecheck(String body) throws IOException {
        Matcher matcher = PRECHECK_PATTERN.matcher(body);
        if (!matcher.matches()) {
            throw new IOException("dump precheck response did not contain parseable details");
        }
        return new DumpPrecheckResult(
                Boolean.parseBoolean(matcher.group(1)),
                Boolean.parseBoolean(matcher.group(2)),
                parseJsonStringArray(matcher.group(3)),
                parseJsonStringArray(matcher.group(4))
        );
    }

    private static DumpImportResult parseDumpImport(String body) throws IOException {
        Matcher rootMatcher = IMPORT_ROOT_PATTERN.matcher(body);
        if (!rootMatcher.find()) {
            throw new IOException("dump load response did not contain root details");
        }
        ModelInstanceRootSummary root = new ModelInstanceRootSummary(
                rootMatcher.group(1),
                rootMatcher.group(2),
                rootMatcher.group(3),
                rootMatcher.group(5)
        );
        return new DumpImportResult(
                root,
                parseCreatedEntityCounts(body),
                parseRequiredInt(CREATED_LINKS_PATTERN, body, "created association-link count"),
                parseRequiredInt(SKIPPED_LINKS_PATTERN, body, "skipped duplicate-link count"),
                parseNamedArray(body, "warnings"),
                parseNamedArray(body, "failedInserts")
        );
    }

    private static Map<String, Integer> parseCreatedEntityCounts(String body) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\"createdEntityCounts\":\\{([^}]*)}").matcher(body);
        if (!matcher.find() || matcher.group(1).isBlank()) {
            return counts;
        }
        Matcher countMatcher = Pattern.compile("\"([^\"]+)\":([0-9]+)").matcher(matcher.group(1));
        while (countMatcher.find()) {
            counts.put(countMatcher.group(1), Integer.parseInt(countMatcher.group(2)));
        }
        return counts;
    }

    private static int parseRequiredInt(Pattern pattern, String body, String name) throws IOException {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IOException("dump load response did not contain " + name);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static List<String> parseNamedArray(String body, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\\[(.*?)]").matcher(body);
        if (!matcher.find()) {
            return List.of();
        }
        return parseJsonStringArray(matcher.group(1));
    }

    private static List<String> parseJsonStringArray(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(content);
        while (matcher.find()) {
            values.add(unescapeJson(matcher.group(1)));
        }
        return List.copyOf(values);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String encodePath(String value) {
        return value.replace(" ", "%20");
    }
}
