package org.vedenemo.web.api.resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vedenemo.app.VedenemoApp;
import org.vedenemo.core.command.ModelCommandJournal;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;
import org.vedenemo.web.api.VedenemoWebApi;
import org.vedenemo.web.api.http.WebApiConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstanceDataResourceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ModelRegistry modelRegistry;
    private Javalin app;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        int port = availablePort();
        WebApiConfig config = new WebApiConfig("127.0.0.1", port, Set.of("*"));
        modelRegistry = new ModelRegistry();
        seedMusicModel();
        ModelCommandJournal commandJournal = new ModelCommandJournal();
        app = VedenemoWebApi.create(
                config,
                modelRegistry,
                VedenemoApp.createSessionManager(modelRegistry, commandJournal),
                commandJournal,
                Optional.empty(),
                "dev",
                java.time.Clock.systemUTC()
        );
        app.start(config.host(), config.port());
        baseUrl = "http://" + config.host() + ":" + config.port();
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void describesDynamicApiForEntitiesAndAssociations() throws Exception {
        String rootId = createRoot(null);
        HttpResponse<String> response = get(rootPath(rootId, "/_api"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"modelAzName\":\"Music\""));
        assertTrue(response.body().contains("\"azName\":\"Artist\""));
        assertTrue(response.body().contains("\"azName\":\"Name\",\"visName\":\"Name\",\"dataType\":\"TEXT\""));
        assertTrue(response.body().contains("\"azName\":\"Album_Artist\""));
        assertTrue(response.body().contains("\"create\":\"/data/{modelAzName}/roots/{instanceRootId}/_links/Album_Artist\""));
    }

    @Test
    void createsListsReadsAndRenamesModelInstanceRoots() throws Exception {
        HttpResponse<String> initial = post("/data/Music/roots", "{}");
        String rootId = responseField(initial, "instanceRootId");
        HttpResponse<String> second = post("/data/Music/roots", """
                {"visName":"Second archive"}
                """);
        HttpResponse<String> list = get("/data/Music/roots");
        HttpResponse<String> renamed = put("/data/Music/roots/" + rootId, """
                {"visName":"Blue Note archive"}
                """);
        HttpResponse<String> afterRename = get("/data/Music/roots/" + rootId);

        assertEquals(201, initial.statusCode());
        assertTrue(initial.body().contains("\"modelAzName\":\"Music\""));
        assertTrue(initial.body().contains("\"modelVersion\":\"1.2.3\""));
        assertTrue(initial.body().contains("\"visName\":null"));
        assertEquals(201, second.statusCode());
        assertEquals(200, list.statusCode());
        assertTrue(list.body().indexOf(rootId) < list.body().indexOf(responseField(second, "instanceRootId")));
        assertEquals(200, renamed.statusCode());
        assertTrue(renamed.body().contains("\"visName\":\"Blue Note archive\""));
        assertEquals(200, afterRename.statusCode());
        assertTrue(afterRename.body().contains("\"visName\":\"Blue Note archive\""));
        assertEquals(400, put("/data/Music/roots/" + rootId, """
                {"visName":" "}
                """).statusCode());
        assertEquals(404, get("/data/Missing/roots").statusCode());
        assertEquals(404, get("/data/Music/roots/00000000-0000-0000-0000-000000000000").statusCode());
    }

    @Test
    void createsListsReadsAndFiltersEntityInstances() throws Exception {
        String rootId = createRoot(null);
        HttpResponse<String> first = post(rootPath(rootId, "/Artist"), """
                {"Website":"https://example.com","Name":"Miles Davis","Rating":99.5}
                """);
        HttpResponse<String> second = post(rootPath(rootId, "/Artist"), """
                {"Name":"Bill Evans","Rating":"98","Website":"https://example.org"}
                """);

        assertEquals(201, first.statusCode());
        assertEquals(201, second.statusCode());
        String firstId = responseField(first, "id");
        assertTrue(first.body().contains("\"values\":{\"Name\":\"Miles Davis\",\"Rating\":99.5,\"Website\":\"https://example.com\"}"));

        HttpResponse<String> list = get(rootPath(rootId, "/Artist"));
        HttpResponse<String> filtered = get(rootPath(rootId, "/Artist?Name=Miles%20Davis"));
        HttpResponse<String> count = get(rootPath(rootId, "/Artist/_count"));
        HttpResponse<String> read = get(rootPath(rootId, "/Artist/" + firstId));

        assertEquals(200, list.statusCode());
        assertTrue(list.body().indexOf("Miles Davis") < list.body().indexOf("Bill Evans"));
        assertEquals(200, filtered.statusCode());
        assertTrue(filtered.body().contains("Miles Davis"));
        assertTrue(!filtered.body().contains("Bill Evans"));
        assertEquals(200, count.statusCode());
        assertTrue(count.body().contains("\"count\":2"));
        assertEquals(200, read.statusCode());
        assertTrue(read.body().contains("\"id\":\"" + firstId + "\""));
    }

    @Test
    void createsLinksAndQueriesThroughRelationshipPredicate() throws Exception {
        String rootId = createRoot(null);
        String artistId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Miles Davis","Rating":99,"Website":"https://example.com"}
                """));
        String otherArtistId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Bill Evans","Rating":98,"Website":"https://example.org"}
                """));
        String albumId = responseId(post(rootPath(rootId, "/Album"), """
                {"Title":"Kind of Blue"}
                """));
        String otherAlbumId = responseId(post(rootPath(rootId, "/Album"), """
                {"Title":"Portrait in Jazz"}
                """));

        HttpResponse<String> link = post(rootPath(rootId, "/_links/Album_Artist"), """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(albumId, artistId));
        post(rootPath(rootId, "/_links/Album_Artist"), """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(otherAlbumId, otherArtistId));

        HttpResponse<String> query = post(rootPath(rootId, "/Album/_query"), """
                {
                  "where": {"equals": {"Title": "Kind of Blue"}},
                  "relationships": [
                    {
                      "associationAzName": "Album_Artist",
                      "direction": "outgoing",
                      "entityAzName": "Artist",
                      "where": {"equals": {"Name": "Miles Davis"}}
                    }
                  ]
                }
                """);
        HttpResponse<String> links = get(rootPath(rootId, "/_links/Album_Artist"));

        assertEquals(201, link.statusCode());
        assertEquals(200, query.statusCode());
        assertTrue(query.body().contains("\"id\":\"" + albumId + "\""));
        assertTrue(!query.body().contains(otherAlbumId));
        assertEquals(200, links.statusCode());
        assertTrue(links.body().contains("\"sourceInstanceId\":\"" + albumId + "\""));
    }

    @Test
    void queriesEntityInstancesWithComparisonOperators() throws Exception {
        String rootId = createRoot(null);
        String milesId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Miles Davis","Rating":99,"Website":"https://example.com"}
                """));
        String billId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Bill Evans","Rating":98,"Website":"https://example.org"}
                """));
        responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"John Coltrane","Rating":100,"Website":"https://example.net"}
                """));
        String shorterId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Wayne Shorter","Rating":97,"Website":"https://example.org/artists/wayne-shorter"}
                """));

        HttpResponse<String> greaterThan = post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Rating", "operator": ">", "value": 98}
                    ]
                  }
                }
                """);
        HttpResponse<String> lessThan = post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Rating", "operator": "<", "value": 99}
                    ]
                  }
                }
                """);
        HttpResponse<String> contains = post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Name", "operator": "contains", "value": "Davis"}
                    ]
                  }
                }
                """);
        HttpResponse<String> urlContains = post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Website", "operator": "contains", "value": "example.org"}
                    ]
                  }
                }
                """);

        assertEquals(200, greaterThan.statusCode());
        assertTrue(greaterThan.body().contains("\"id\":\"" + milesId + "\""));
        assertTrue(!greaterThan.body().contains("\"id\":\"" + billId + "\""));
        assertEquals(200, lessThan.statusCode());
        assertTrue(lessThan.body().contains("\"id\":\"" + billId + "\""));
        assertTrue(!lessThan.body().contains("\"id\":\"" + milesId + "\""));
        assertEquals(200, contains.statusCode());
        assertTrue(contains.body().contains("\"id\":\"" + milesId + "\""));
        assertTrue(!contains.body().contains("\"id\":\"" + billId + "\""));
        assertEquals(200, urlContains.statusCode());
        assertTrue(urlContains.body().contains("\"id\":\"" + shorterId + "\""));
        assertTrue(!urlContains.body().contains("\"id\":\"" + milesId + "\""));
    }

    @Test
    void keepsEntityInstancesIsolatedByRoot() throws Exception {
        String firstRootId = createRoot("First archive");
        String secondRootId = createRoot("Second archive");

        responseId(post(rootPath(firstRootId, "/Artist"), """
                {"Name":"Miles Davis"}
                """));
        responseId(post(rootPath(secondRootId, "/Artist"), """
                {"Name":"Bill Evans"}
                """));

        HttpResponse<String> firstList = get(rootPath(firstRootId, "/Artist"));
        HttpResponse<String> secondList = get(rootPath(secondRootId, "/Artist"));

        assertEquals(200, firstList.statusCode());
        assertTrue(firstList.body().contains("Miles Davis"));
        assertTrue(!firstList.body().contains("Bill Evans"));
        assertEquals(200, secondList.statusCode());
        assertTrue(secondList.body().contains("Bill Evans"));
        assertTrue(!secondList.body().contains("Miles Davis"));
    }

    @Test
    void rejectsInvalidDynamicDataRequests() throws Exception {
        String rootId = createRoot(null);
        String artistId = responseId(post(rootPath(rootId, "/Artist"), """
                {"Name":"Miles Davis","Rating":99,"Website":"https://example.com"}
                """));
        String albumId = responseId(post(rootPath(rootId, "/Album"), """
                {"Title":"Kind of Blue"}
                """));

        assertEquals(404, get(rootPath("00000000-0000-0000-0000-000000000000", "/_api")).statusCode());
        assertEquals(404, post(rootPath(rootId, "/Missing"), "{}").statusCode());
        assertEquals(404, post(rootPath(rootId, "/_links/Missing"), """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(albumId, artistId)).statusCode());
        assertEquals(400, post(rootPath(rootId, "/Artist"), """
                {"Unknown":"value"}
                """).statusCode());
        assertEquals(400, post(rootPath(rootId, "/Artist"), """
                {"Rating":"not numeric"}
                """).statusCode());
        assertEquals(400, post(rootPath(rootId, "/Artist"), """
                {"Website":"/relative"}
                """).statusCode());
        assertEquals(400, post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Name", "operator": ">", "value": "Miles Davis"}
                    ]
                  }
                }
                """).statusCode());
        assertEquals(400, post(rootPath(rootId, "/Artist/_query"), """
                {
                  "where": {
                    "comparisons": [
                      {"attributeAzName": "Rating", "operator": "contains", "value": 99}
                    ]
                  }
                }
                """).statusCode());
        assertEquals(400, post(rootPath(rootId, "/_links/Album_Artist"), """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(artistId, albumId)).statusCode());
    }

    private String responseId(HttpResponse<String> response) throws IOException {
        assertEquals(201, response.statusCode());
        return responseField(response, "id");
    }

    private String createRoot(String visName) throws IOException, InterruptedException {
        String body = visName == null ? "{}" : """
                {"visName":"%s"}
                """.formatted(visName);
        return responseField(post("/data/Music/roots", body), "instanceRootId");
    }

    private String responseField(HttpResponse<String> response, String fieldName) throws IOException {
        return objectMapper.readValue(response.body(), MAP_TYPE).get(fieldName).toString();
    }

    private String rootPath(String instanceRootId, String suffix) {
        return "/data/Music/roots/" + instanceRootId + suffix;
    }

    private void seedMusicModel() {
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Music", "Music", "1.2.3"));
        VEntity artist = modelRoot.addEntity(new VEntity("Artist", "Artist", modelRoot.version()));
        artist.addAttribute(new VAttribute("Name", "Name", DataType.TEXT, modelRoot.version()));
        artist.addAttribute(new VAttribute("Rating", "Rating", DataType.NUMERIC, modelRoot.version()));
        artist.addAttribute(new VAttribute("Website", "Website", DataType.URL, modelRoot.version()));
        VEntity album = modelRoot.addEntity(new VEntity("Album", "Album", modelRoot.version()));
        album.addAttribute(new VAttribute("Title", "Title", DataType.TEXT, modelRoot.version()));
        modelRoot.addAssociation(new OwnershipAssociation(
                "Album_Artist",
                "artist",
                "Album",
                "Artist",
                Cardinality.parse("1"),
                modelRoot.version()
        ));
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
