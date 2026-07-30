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

        EntityInstance instance = fixture.service.createEntityInstance("Music", "Artist", values);
        List<EntityInstance> listed = fixture.service.listEntityInstances("Music", "Artist", Map.of("Rating", new BigDecimal("99.50")));

        assertEquals("Music", instance.modelAzName());
        assertEquals("1.2.3", instance.modelVersion());
        assertEquals("Artist", instance.entityAzName());
        assertEquals(List.of("Name", "Rating", "Website"), List.copyOf(instance.values().keySet()));
        assertEquals(1, listed.size());
        assertEquals(instance.id(), listed.getFirst().id());
        assertEquals(1, fixture.service.countEntityInstances("Music", "Artist"));
        assertEquals(0, fixture.service.countEntityInstances("Music", "Album"));
    }

    @Test
    void rejectsUnknownModelEntityAttributeAndInvalidValues() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Missing", "Artist", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", "Missing", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", "Artist", Map.of("Unknown", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", "Artist", Map.of("Rating", "not numeric")));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createEntityInstance("Music", "Artist", Map.of("Website", "/relative")));
    }

    @Test
    void createsAssociationLinksAndQueriesThroughRelatedEntityAttributes() {
        Fixture fixture = fixture();
        EntityInstance artist = fixture.service.createEntityInstance("Music", "Artist", Map.of("Name", "Miles Davis"));
        EntityInstance otherArtist = fixture.service.createEntityInstance("Music", "Artist", Map.of("Name", "Bill Evans"));
        EntityInstance album = fixture.service.createEntityInstance("Music", "Album", Map.of("Title", "Kind of Blue"));
        EntityInstance otherAlbum = fixture.service.createEntityInstance("Music", "Album", Map.of("Title", "Portrait in Jazz"));

        AssociationInstanceLink link = fixture.service.createAssociationLink(
                "Music",
                "Album_Artist",
                album.id().value(),
                artist.id().value()
        );
        fixture.service.createAssociationLink("Music", "Album_Artist", otherAlbum.id().value(), otherArtist.id().value());

        List<EntityInstance> matches = fixture.service.queryEntityInstances(
                "Music",
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
    void rejectsInvalidAssociationLinks() {
        Fixture fixture = fixture();
        EntityInstance artist = fixture.service.createEntityInstance("Music", "Artist", Map.of("Name", "Miles Davis"));
        EntityInstance album = fixture.service.createEntityInstance("Music", "Album", Map.of("Title", "Kind of Blue"));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink("Music", "Missing", album.id().value(), artist.id().value()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink("Music", "Album_Artist", artist.id().value(), album.id().value()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createAssociationLink(
                        "Music",
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
        return new Fixture(new ModelInstanceService(modelRegistry, new ModelInstanceRegistry()));
    }

    private record Fixture(ModelInstanceService service) {
    }
}
