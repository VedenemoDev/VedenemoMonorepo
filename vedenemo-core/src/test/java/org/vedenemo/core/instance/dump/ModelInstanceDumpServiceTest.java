package org.vedenemo.core.instance.dump;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.instance.EntityInstance;
import org.vedenemo.core.instance.ModelInstanceRegistry;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelInstanceDumpServiceTest {

    @Test
    void exportsOneRootWithNullsForOmittedValues() {
        Fixture fixture = fixture("1.2.3");
        EntityInstance artist = fixture.instanceService.createEntityInstance(
                "Music",
                fixture.rootId,
                "Artist",
                Map.of("Name", "Miles Davis")
        );

        ModelInstanceDump dump = fixture.dumpService.exportDump("Music", fixture.rootId, Instant.parse("2026-08-21T05:00:00Z"));

        assertEquals(ModelInstanceDump.FORMAT, dump.format());
        assertEquals("Music", dump.model().azName());
        assertEquals("1.2.3", dump.model().version());
        assertEquals(fixture.rootId, dump.root().sourceInstanceRootId());
        DumpEntityRecord record = dump.entities().stream()
                .filter(group -> group.entityAzName().equals("Artist"))
                .findFirst()
                .orElseThrow()
                .records()
                .getFirst();
        assertEquals(artist.id().value(), record.dumpId());
        assertEquals("Miles Davis", record.values().get("Name"));
        assertTrue(record.values().containsKey("Rating"));
        assertEquals(null, record.values().get("Rating"));
    }

    @Test
    void importsNullsAsOmittedValuesAndSkipsDuplicateLinks() {
        Fixture fixture = fixture("1.2.3");
        ModelInstanceDump dump = new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Instant.parse("2026-08-21T05:00:00Z"),
                new DumpModel("Music", "Music", "1.2.3"),
                new DumpRoot("source-root", "Imported root"),
                List.of(
                        new DumpEntityGroup("Artist", List.of(new DumpEntityRecord("artist-1", valuesWithNullRating()))),
                        new DumpEntityGroup("Album", List.of(new DumpEntityRecord("album-1", Map.of("Title", "Kind of Blue"))))
                ),
                List.of(
                        new DumpAssociationLink("Album_Artist", "album-1", "artist-1"),
                        new DumpAssociationLink("Album_Artist", "album-1", "artist-1")
                )
        );

        ModelInstanceDumpImportResult result = fixture.dumpService.importDump("Music", dump, false);

        assertEquals("Imported root", result.root().visName());
        assertEquals(1, result.createdEntityCounts().get("Artist"));
        assertEquals(1, result.createdEntityCounts().get("Album"));
        assertEquals(1, result.createdAssociationLinkCount());
        assertEquals(1, result.skippedDuplicateLinkCount());
        assertEquals(1, fixture.instanceService.listEntityInstances("Music", result.root().instanceRootId(), "Artist", Map.of("Name", "Miles Davis")).size());
    }

    @Test
    void reportsSchemaAndVersionPrecheckDiagnosticsBeforeImport() {
        Fixture fixture = fixture("1.0.0");
        ModelInstanceDump dump = new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Instant.parse("2026-08-21T05:00:00Z"),
                new DumpModel("Music", "Music", "2.0.0"),
                new DumpRoot("source-root", "Imported root"),
                List.of(new DumpEntityGroup("Artist", List.of(new DumpEntityRecord("artist-1", Map.of("Missing", "value"))))),
                List.of(new DumpAssociationLink("Missing_Association", "album-1", "artist-1"))
        );

        ModelInstanceDumpPrecheckResult result = fixture.dumpService.precheck("Music", dump);

        assertFalse(result.importable());
        assertTrue(result.diagnostics().contains("dump model version 2.0.0 is newer than loaded model version 1.0.0"));
        assertTrue(result.diagnostics().contains("attribute not found: Artist.Missing"));
        assertTrue(result.diagnostics().contains("association not found: Missing_Association"));
    }

    private static Fixture fixture(String version) {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Music", "Music", version));
        VEntity artist = modelRoot.addEntity(new VEntity("Artist", "Artist", modelRoot.version()));
        artist.addAttribute(new VAttribute("Name", "Name", DataType.TEXT, modelRoot.version()));
        artist.addAttribute(new VAttribute("Rating", "Rating", DataType.NUMERIC, modelRoot.version()));
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
        ModelInstanceService instanceService = new ModelInstanceService(modelRegistry, new ModelInstanceRegistry());
        String rootId = instanceService.createRoot("Music", "Source root").instanceRootId();
        return new Fixture(instanceService, new ModelInstanceDumpService(instanceService), rootId);
    }

    private static Map<String, Object> valuesWithNullRating() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("Name", "Miles Davis");
        values.put("Rating", null);
        return values;
    }

    private record Fixture(ModelInstanceService instanceService, ModelInstanceDumpService dumpService, String rootId) {
    }
}
