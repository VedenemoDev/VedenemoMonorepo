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
        HttpResponse<String> response = get("/data/Music/_api");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"modelAzName\":\"Music\""));
        assertTrue(response.body().contains("\"azName\":\"Artist\""));
        assertTrue(response.body().contains("\"azName\":\"Name\",\"visName\":\"Name\",\"dataType\":\"TEXT\""));
        assertTrue(response.body().contains("\"azName\":\"Album_Artist\""));
        assertTrue(response.body().contains("\"create\":\"/data/{modelAzName}/_links/Album_Artist\""));
    }

    @Test
    void readsAndRenamesModelInstanceRoot() throws Exception {
        HttpResponse<String> initial = get("/data/Music/_instance-root");
        HttpResponse<String> renamed = put("/data/Music/_instance-root", """
                {"visName":"Blue Note archive"}
                """);
        HttpResponse<String> afterRename = get("/data/Music/_instance-root");

        assertEquals(200, initial.statusCode());
        assertTrue(initial.body().contains("\"modelAzName\":\"Music\""));
        assertTrue(initial.body().contains("\"modelVersion\":\"1.2.3\""));
        assertTrue(initial.body().contains("\"visName\":\"Model instance 1\""));
        assertEquals(200, renamed.statusCode());
        assertTrue(renamed.body().contains("\"visName\":\"Blue Note archive\""));
        assertEquals(200, afterRename.statusCode());
        assertTrue(afterRename.body().contains("\"visName\":\"Blue Note archive\""));
        assertEquals(400, put("/data/Music/_instance-root", """
                {"visName":" "}
                """).statusCode());
        assertEquals(404, get("/data/Missing/_instance-root").statusCode());
    }

    @Test
    void createsListsReadsAndFiltersEntityInstances() throws Exception {
        HttpResponse<String> first = post("/data/Music/Artist", """
                {"Website":"https://example.com","Name":"Miles Davis","Rating":99.5}
                """);
        HttpResponse<String> second = post("/data/Music/Artist", """
                {"Name":"Bill Evans","Rating":"98","Website":"https://example.org"}
                """);

        assertEquals(201, first.statusCode());
        assertEquals(201, second.statusCode());
        Map<String, Object> firstBody = objectMapper.readValue(first.body(), MAP_TYPE);
        String firstId = firstBody.get("id").toString();
        assertTrue(first.body().contains("\"values\":{\"Name\":\"Miles Davis\",\"Rating\":99.5,\"Website\":\"https://example.com\"}"));

        HttpResponse<String> list = get("/data/Music/Artist");
        HttpResponse<String> filtered = get("/data/Music/Artist?Name=Miles%20Davis");
        HttpResponse<String> count = get("/data/Music/Artist/_count");
        HttpResponse<String> read = get("/data/Music/Artist/" + firstId);

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
        String artistId = responseId(post("/data/Music/Artist", """
                {"Name":"Miles Davis","Rating":99,"Website":"https://example.com"}
                """));
        String otherArtistId = responseId(post("/data/Music/Artist", """
                {"Name":"Bill Evans","Rating":98,"Website":"https://example.org"}
                """));
        String albumId = responseId(post("/data/Music/Album", """
                {"Title":"Kind of Blue"}
                """));
        String otherAlbumId = responseId(post("/data/Music/Album", """
                {"Title":"Portrait in Jazz"}
                """));

        HttpResponse<String> link = post("/data/Music/_links/Album_Artist", """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(albumId, artistId));
        post("/data/Music/_links/Album_Artist", """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(otherAlbumId, otherArtistId));

        HttpResponse<String> query = post("/data/Music/Album/_query", """
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
        HttpResponse<String> links = get("/data/Music/_links/Album_Artist");

        assertEquals(201, link.statusCode());
        assertEquals(200, query.statusCode());
        assertTrue(query.body().contains("\"id\":\"" + albumId + "\""));
        assertTrue(!query.body().contains(otherAlbumId));
        assertEquals(200, links.statusCode());
        assertTrue(links.body().contains("\"sourceInstanceId\":\"" + albumId + "\""));
    }

    @Test
    void rejectsInvalidDynamicDataRequests() throws Exception {
        String artistId = responseId(post("/data/Music/Artist", """
                {"Name":"Miles Davis","Rating":99,"Website":"https://example.com"}
                """));
        String albumId = responseId(post("/data/Music/Album", """
                {"Title":"Kind of Blue"}
                """));

        assertEquals(404, get("/data/Missing/_api").statusCode());
        assertEquals(404, post("/data/Music/Missing", "{}").statusCode());
        assertEquals(404, post("/data/Music/_links/Missing", """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(albumId, artistId)).statusCode());
        assertEquals(400, post("/data/Music/Artist", """
                {"Unknown":"value"}
                """).statusCode());
        assertEquals(400, post("/data/Music/Artist", """
                {"Rating":"not numeric"}
                """).statusCode());
        assertEquals(400, post("/data/Music/Artist", """
                {"Website":"/relative"}
                """).statusCode());
        assertEquals(400, post("/data/Music/_links/Album_Artist", """
                {"sourceInstanceId":"%s","targetInstanceId":"%s"}
                """.formatted(artistId, albumId)).statusCode());
    }

    private String responseId(HttpResponse<String> response) throws IOException {
        assertEquals(201, response.statusCode());
        return objectMapper.readValue(response.body(), MAP_TYPE).get("id").toString();
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
