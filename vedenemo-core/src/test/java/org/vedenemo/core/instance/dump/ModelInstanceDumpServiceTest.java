package org.vedenemo.core.instance.dump;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.instance.EntityInstance;
import org.vedenemo.core.instance.LocationAreaValue;
import org.vedenemo.core.instance.LocationLineValue;
import org.vedenemo.core.instance.LocationValue;
import org.vedenemo.core.instance.ModelInstanceRegistry;
import org.vedenemo.core.instance.ModelInstanceService;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.model.ValueSet;
import org.vedenemo.core.model.ValueSetEntry;
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
    void exportsAndImportsLocationValuesAsStructuredValues() {
        Fixture fixture = fixture("1.2.3");
        EntityInstance artist = fixture.instanceService.createEntityInstance(
                "Music",
                fixture.rootId,
                "Artist",
                Map.of("Name", "Miles Davis", "Location", Map.of("latitude", 62.1234567, "longitude", 30.1234567))
        );

        ModelInstanceDump dump = fixture.dumpService.exportDump("Music", fixture.rootId, Instant.parse("2026-08-21T05:00:00Z"));
        DumpEntityRecord record = dump.entities().stream()
                .filter(group -> group.entityAzName().equals("Artist"))
                .findFirst()
                .orElseThrow()
                .records()
                .getFirst();
        ModelInstanceDumpImportResult result = fixture.dumpService.importDump("Music", dump, false);
        EntityInstance imported = fixture.instanceService.listEntityInstances(
                "Music",
                result.root().instanceRootId(),
                "Artist",
                Map.of("Location", Map.of("latitude", 62.1234567, "longitude", 30.1234567))
        ).getFirst();

        assertEquals(artist.id().value(), record.dumpId());
        assertEquals(new LocationValue(62.1234567, 30.1234567), record.values().get("Location"));
        assertEquals("Miles Davis", imported.values().get("Name").value());
        assertEquals(new LocationValue(62.1234567, 30.1234567), imported.values().get("Location").value());
    }

    @Test
    void exportsAndImportsLocationLineAndAreaValuesAsStructuredValues() {
        Fixture fixture = fixture("1.2.3");
        Map<String, Object> path = Map.of("locations", List.of(
                Map.of("latitude", 62.1234567, "longitude", 30.1234567),
                Map.of("latitude", 62.2234567, "longitude", 30.2234567)
        ));
        Map<String, Object> boundary = Map.of("boundary", List.of(
                Map.of("latitude", 62.1234567, "longitude", 30.1234567),
                Map.of("latitude", 62.2234567, "longitude", 30.2234567),
                Map.of("latitude", 62.1234567, "longitude", 30.3234567)
        ));
        fixture.instanceService.createEntityInstance(
                "Music",
                fixture.rootId,
                "Artist",
                Map.of("Name", "Spatial Artist", "Path", path, "Boundary", boundary)
        );

        ModelInstanceDump dump = fixture.dumpService.exportDump("Music", fixture.rootId, Instant.parse("2026-08-21T05:00:00Z"));
        DumpEntityRecord record = dump.entities().stream()
                .filter(group -> group.entityAzName().equals("Artist"))
                .findFirst()
                .orElseThrow()
                .records()
                .getFirst();
        ModelInstanceDumpPrecheckResult precheck = fixture.dumpService.precheck("Music", dump);
        ModelInstanceDumpImportResult result = fixture.dumpService.importDump("Music", dump, false);
        EntityInstance imported = fixture.instanceService.listEntityInstances(
                "Music",
                result.root().instanceRootId(),
                "Artist",
                Map.of("Path", path, "Boundary", boundary)
        ).getFirst();

        assertTrue(precheck.importable());
        assertEquals(new LocationLineValue(List.of(
                new LocationValue(62.1234567, 30.1234567),
                new LocationValue(62.2234567, 30.2234567)
        )), record.values().get("Path"));
        assertEquals(new LocationAreaValue(List.of(
                new LocationValue(62.1234567, 30.1234567),
                new LocationValue(62.2234567, 30.2234567),
                new LocationValue(62.1234567, 30.3234567)
        )), record.values().get("Boundary"));
        assertEquals(record.values().get("Path"), imported.values().get("Path").value());
        assertEquals(record.values().get("Boundary"), imported.values().get("Boundary").value());
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

    @Test
    void reportsInvalidLocationDumpValuesDuringPrecheck() {
        Fixture fixture = fixture("1.0.0");
        ModelInstanceDump dump = new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Instant.parse("2026-08-21T05:00:00Z"),
                new DumpModel("Music", "Music", "1.0.0"),
                new DumpRoot("source-root", "Imported root"),
                List.of(new DumpEntityGroup("Artist", List.of(new DumpEntityRecord("artist-1", Map.of("Location", Map.of("latitude", "62.0", "longitude", 30.0)))))),
                List.of()
        );

        ModelInstanceDumpPrecheckResult result = fixture.dumpService.precheck("Music", dump);

        assertFalse(result.importable());
        assertTrue(result.diagnostics().contains("attribute type mismatch: Artist.Location expects LOCATION but dump value is MapN"));
    }

    @Test
    void reportsInvalidLocationLineAndAreaDumpValuesDuringPrecheck() {
        Fixture fixture = fixture("1.0.0");
        ModelInstanceDump dump = new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Instant.parse("2026-08-21T05:00:00Z"),
                new DumpModel("Music", "Music", "1.0.0"),
                new DumpRoot("source-root", "Imported root"),
                List.of(new DumpEntityGroup("Artist", List.of(new DumpEntityRecord("artist-1", Map.of(
                        "Path", Map.of("locations", List.of(Map.of("latitude", 62.0, "longitude", 30.0))),
                        "Boundary", Map.of("boundary", List.of(
                                Map.of("latitude", 62.0, "longitude", 30.0),
                                Map.of("latitude", 63.0, "longitude", 31.0),
                                Map.of("latitude", 62.0, "longitude", 30.0)
                        ))
                ))))),
                List.of()
        );

        ModelInstanceDumpPrecheckResult result = fixture.dumpService.precheck("Music", dump);

        assertFalse(result.importable());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.contains("attribute type mismatch: Artist.Path expects LOCATION_LINE")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.contains("attribute type mismatch: Artist.Boundary expects LOCATION_AREA")));
    }

    @Test
    void reportsValueSetViolationsDuringPrecheck() {
        Fixture fixture = fixture("1.0.0");
        ModelInstanceDump dump = new ModelInstanceDump(
                ModelInstanceDump.FORMAT,
                ModelInstanceDump.FORMAT_VERSION,
                Instant.parse("2026-08-21T05:00:00Z"),
                new DumpModel("Music", "Music", "1.0.0"),
                new DumpRoot("source-root", "Imported root"),
                List.of(new DumpEntityGroup("Artist", List.of(new DumpEntityRecord("artist-1", Map.of("Species", "BIRCH"))))),
                List.of()
        );

        ModelInstanceDumpPrecheckResult result = fixture.dumpService.precheck("Music", dump);

        assertFalse(result.importable());
        assertTrue(result.diagnostics().contains("attribute value outside ValueSet: Artist.Species must be one of TreeSpecies"));
    }

    private static Fixture fixture(String version) {
        ModelRegistry modelRegistry = new ModelRegistry();
        ModelRoot modelRoot = modelRegistry.add(ModelRoot.create("Music", "Music", version));
        modelRoot.addValueSet(new ValueSet("TreeSpecies", DataType.TEXT, List.of(
                new ValueSetEntry("PINE", "Pine"),
                new ValueSetEntry("SPRUCE", "Spruce")
        )));
        VEntity artist = modelRoot.addEntity(new VEntity("Artist", "Artist", modelRoot.version()));
        artist.addAttribute(new VAttribute("Name", "Name", DataType.TEXT, modelRoot.version()));
        artist.addAttribute(new VAttribute("Rating", "Rating", DataType.NUMERIC, modelRoot.version()));
        artist.addAttribute(new VAttribute("Location", "Location", DataType.LOCATION, modelRoot.version()));
        artist.addAttribute(new VAttribute("Path", "Path", DataType.LOCATION_LINE, modelRoot.version()));
        artist.addAttribute(new VAttribute("Boundary", "Boundary", DataType.LOCATION_AREA, modelRoot.version()));
        artist.addAttribute(new VAttribute("Species", "Species", DataType.TEXT, modelRoot.version(), null, null, "TreeSpecies"));
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
