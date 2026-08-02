package org.vedenemo.core.instance;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.Cardinality;
import org.vedenemo.core.model.DataType;
import org.vedenemo.core.model.ModelRoot;
import org.vedenemo.core.model.OwnershipAssociation;
import org.vedenemo.core.model.VAttribute;
import org.vedenemo.core.model.VEntity;
import org.vedenemo.core.registry.ModelRegistry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ModelInstanceServiceTest {

    @Test
    void createsAndListsInstancesWithSchemaValidatedValuesInAttributeOrder() {
        Fixture fixture = fixture();
        Map<String, Object> values = Map.of(
                "Website", "https://example.com",
                "Name", "Miles Davis",
                "Rating", "99.5"
        );
        String rootId = fixture.rootId();

        EntityInstance instance = fixture.service.createEntityInstance("Music", rootId, "Artist", values);
        List<EntityInstance> listed = fixture.service.listEntityInstances("Music", rootId, "Artist", Map.of("Rating", new BigDecimal("99.50")));

        assertEquals("Music", instance.modelAzName());
        assertEquals("1.2.3", instance.modelVersion());
        assertEquals("Artist", instance.entityAzName());
        assertEquals(List.of("Name", "Rating", "Website"), List.copyOf(instance.values().keySet()));
        assertEquals(1, listed.size());
        assertEquals(instance.id(), listed.getFirst().id());
        assertEquals(1, fixture.service.countEntityInstances("Music", rootId, "Artist"));
        assertEquals(0, fixture.service.countEntityInstances("Music", rootId, "Album"));
    }

    @Test
    void updatesExistingInstanceWithSchemaValidatedValues() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance instance = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of(
                "Name", "Miles Davis",
                "Rating", 99
        ));

        EntityInstance updated = fixture.service.updateEntityInstance("Music", rootId, "Artist", instance.id().value(), Map.of(
                "Name", "Miles Davis Quintet",
                "Website", "https://example.com/miles"
        ));

        assertEquals(instance.id(), updated.id());
        assertEquals("Miles Davis Quintet", updated.values().get("Name").value());
        assertEquals("https://example.com/miles", updated.values().get("Website").value());
        assertEquals(null, updated.values().get("Rating"));
        assertEquals(1, fixture.service.countEntityInstances("Music", rootId, "Artist"));
        assertEquals("Miles Davis Quintet", fixture.service.readEntityInstance("Music", rootId, "Artist", instance.id().value())
                .values()
                .get("Name")
                .value());
    }

    @Test
    void rejectsCreateOrUpdateWithoutAnySubmittedAttributeData() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance instance = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Miles Davis"));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.updateEntityInstance("Music", rootId, "Artist", instance.id().value(), Map.of()));
        assertEquals(1, fixture.service.countEntityInstances("Music", rootId, "Artist"));
    }

    @Test
    void readsAndRenamesModelInstanceRootMetadata() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();

        ModelInstanceRoot defaultRoot = fixture.service.readRoot("Music", rootId);
        ModelInstanceRoot renamed = fixture.service.renameRoot("Music", rootId, "Blue Note archive");

        assertEquals(rootId, defaultRoot.instanceRootId());
        assertEquals("Music", defaultRoot.modelAzName());
        assertEquals("1.2.3", defaultRoot.modelVersion());
        assertEquals(null, defaultRoot.visName());
        assertEquals("Blue Note archive", renamed.visName());
        assertEquals("Blue Note archive", fixture.service.readRoot("Music", rootId).visName());
        assertEquals(List.of(rootId), fixture.service.listRoots("Music").stream().map(ModelInstanceRoot::instanceRootId).toList());
        assertThrows(IllegalArgumentException.class, () -> fixture.service.renameRoot("Music", rootId, " "));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.renameRoot("Missing", rootId, "Archive"));
    }

    @Test
    void isolatesInstancesBetweenRootsForSameModel() {
        Fixture fixture = fixture();
        String firstRootId = fixture.rootId();
        String secondRootId = fixture.service.createRoot("Music", "Other archive").instanceRootId();

        fixture.service.createEntityInstance("Music", firstRootId, "Artist", Map.of("Name", "Miles Davis"));
        fixture.service.createEntityInstance("Music", secondRootId, "Artist", Map.of("Name", "Bill Evans"));

        assertEquals(1, fixture.service.countEntityInstances("Music", firstRootId, "Artist"));
        assertEquals(1, fixture.service.countEntityInstances("Music", secondRootId, "Artist"));
        assertEquals("Miles Davis", fixture.service.listEntityInstances("Music", firstRootId, "Artist", Map.of())
                .getFirst()
                .values()
                .get("Name")
                .value());
        assertEquals("Bill Evans", fixture.service.listEntityInstances("Music", secondRootId, "Artist", Map.of())
                .getFirst()
                .values()
                .get("Name")
                .value());
    }

    @Test
    void rejectsUnknownModelEntityAttributeAndInvalidValues() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Missing", rootId, "Artist", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", "00000000-0000-0000-0000-000000000000", "Artist", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", rootId, "Missing", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Unknown", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Rating", "not numeric")));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Website", "/relative")));
    }

    @Test
    void createsAssociationLinksAndQueriesThroughRelatedEntityAttributes() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance artist = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Miles Davis"));
        EntityInstance otherArtist = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Bill Evans"));
        EntityInstance album = fixture.service.createEntityInstance("Music", rootId, "Album", Map.of("Title", "Kind of Blue"));
        EntityInstance otherAlbum = fixture.service.createEntityInstance("Music", rootId, "Album", Map.of("Title", "Portrait in Jazz"));

        AssociationInstanceLink link = fixture.service.createAssociationLink(
                "Music",
                rootId,
                "Album_Artist",
                album.id().value(),
                artist.id().value()
        );
        fixture.service.createAssociationLink("Music", rootId, "Album_Artist", otherAlbum.id().value(), otherArtist.id().value());

        List<EntityInstance> matches = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Album",
                new EntityInstanceQuery(
                        Map.of("Title", "Kind of Blue"),
                        List.of(new RelationshipPredicate(
                                "Album_Artist",
                                RelationshipDirection.OUTGOING,
                                "Artist",
                                Map.of("Name", "Miles Davis")
                        ))
                )
        );

        assertEquals("Album_Artist", link.associationAzName());
        assertEquals(1, matches.size());
        assertEquals(album.id(), matches.getFirst().id());
    }

    @Test
    void queriesWithScalarComparisonOperators() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance miles = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Miles Davis", "Rating", 99));
        EntityInstance bill = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Bill Evans", "Rating", 98));
        EntityInstance coltrane = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "John Coltrane", "Rating", 100));
        EntityInstance shorter = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Wayne Shorter", "Rating", 97, "Website", "https://example.org/artists/wayne-shorter"));

        List<EntityInstance> greaterThan = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Rating", ScalarComparisonOperator.GREATER_THAN, 98)),
                        List.of()
                )
        );
        List<EntityInstance> lessThan = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Rating", ScalarComparisonOperator.LESS_THAN, 99)),
                        List.of()
                )
        );
        List<EntityInstance> contains = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Name", ScalarComparisonOperator.CONTAINS, "davis")),
                        List.of()
                )
        );
        List<EntityInstance> urlContains = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Website", ScalarComparisonOperator.CONTAINS, "example.org")),
                        List.of()
                )
        );

        assertEquals(List.of(miles.id()), contains.stream().map(EntityInstance::id).toList());
        assertEquals(1, urlContains.size());
        assertEquals("Wayne Shorter", urlContains.getFirst().values().get("Name").value());
        assertEquals(List.of(miles.id(), coltrane.id()), greaterThan.stream().map(EntityInstance::id).toList());
        assertEquals(List.of(bill.id(), shorter.id()), lessThan.stream().map(EntityInstance::id).toList());
    }

    @Test
    void listsAndQueriesAllMatchingInstancesWithoutImplicitLimit() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();

        for (int index = 0; index < 105; index++) {
            fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Artist " + index));
        }

        List<EntityInstance> listed = fixture.service.listEntityInstances("Music", rootId, "Artist", Map.of());
        List<EntityInstance> queried = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(Map.of(), List.of(), List.of())
        );

        assertEquals(105, listed.size());
        assertEquals(105, queried.size());
        assertEquals("Artist 0", queried.getFirst().values().get("Name").value());
        assertEquals("Artist 104", queried.getLast().values().get("Name").value());
    }

    @Test
    void relationshipQueriesReturnAllMatchesWithoutImplicitLimit() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance artist = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Catalog Artist"));

        for (int index = 0; index < 105; index++) {
            EntityInstance album = fixture.service.createEntityInstance("Music", rootId, "Album", Map.of("Title", "Album " + index));
            fixture.service.createAssociationLink("Music", rootId, "Album_Artist", album.id().value(), artist.id().value());
        }

        List<EntityInstance> matches = fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Album",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(),
                        List.of(new RelationshipPredicate(
                                "Album_Artist",
                                RelationshipDirection.OUTGOING,
                                "Artist",
                                Map.of(),
                                List.of()
                        ))
                )
        );

        assertEquals(105, matches.size());
        assertEquals("Album 0", matches.getFirst().values().get("Title").value());
        assertEquals("Album 104", matches.getLast().values().get("Title").value());
    }

    @Test
    void rejectsComparisonOperatorsThatDoNotMatchAttributeType() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Miles Davis", "Rating", 99));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Name", ScalarComparisonOperator.GREATER_THAN, "Miles Davis")),
                        List.of()
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.queryEntityInstances(
                "Music",
                rootId,
                "Artist",
                new EntityInstanceQuery(
                        Map.of(),
                        List.of(new ScalarComparison("Rating", ScalarComparisonOperator.CONTAINS, 99)),
                        List.of()
                )
        ));
    }

    @Test
    void rejectsInvalidAssociationLinks() {
        Fixture fixture = fixture();
        String rootId = fixture.rootId();
        EntityInstance artist = fixture.service.createEntityInstance("Music", rootId, "Artist", Map.of("Name", "Miles Davis"));
        EntityInstance album = fixture.service.createEntityInstance("Music", rootId, "Album", Map.of("Title", "Kind of Blue"));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink("Music", rootId, "Missing", album.id().value(), artist.id().value()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink("Music", rootId, "Album_Artist", artist.id().value(), album.id().value()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink(
                        "Music",
                        rootId,
                        "Album_Artist",
                        "00000000-0000-0000-0000-000000000000",
                        artist.id().value()
                ));
    }

    private static Fixture fixture() {
        ModelRegistry modelRegistry = new ModelRegistry();
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
        ModelInstanceService service = new ModelInstanceService(modelRegistry, new ModelInstanceRegistry());
        String rootId = service.createRoot("Music", null).instanceRootId();
        return new Fixture(service, rootId);
    }

    private record Fixture(ModelInstanceService service, String rootId) {
    }
}
