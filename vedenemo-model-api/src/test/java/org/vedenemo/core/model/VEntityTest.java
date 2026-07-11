package org.vedenemo.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class VEntityTest {

    private static final ModelVersion VERSION = new ModelVersion(1, 0, 0);

    @Test
    void acceptsValidData() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);

        assertEquals("Customer", entity.azName());
        assertEquals("Customer", entity.visName());
        assertEquals(VERSION, entity.activeSince());
    }

    @Test
    void preservesAttributeInsertionOrder() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        VAttribute first = attribute("Name");
        VAttribute second = attribute("Email");

        entity.addAttribute(first);
        entity.addAttribute(second);

        assertEquals(List.of(first, second), entity.attributes());
    }

    @Test
    void rejectsDuplicateAttributeAzName() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        entity.addAttribute(attribute("Name"));

        assertThrows(IllegalArgumentException.class, () -> entity.addAttribute(attribute("Name")));
    }

    @Test
    void rejectsCaseOnlyDuplicateAttributeAzName() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        entity.addAttribute(attribute("Name"));

        assertThrows(IllegalArgumentException.class, () -> entity.addAttribute(attribute("name")));
    }

    @Test
    void removesAttributeByAzName() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        VAttribute attribute = attribute("Name");
        entity.addAttribute(attribute);

        assertEquals(attribute, entity.removeAttribute("name").orElseThrow());
        assertTrue(entity.attributes().isEmpty());
    }

    @Test
    void removesAttributeByInstance() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        VAttribute attribute = attribute("Name");
        entity.addAttribute(attribute);

        assertTrue(entity.removeAttribute(attribute));
        assertTrue(entity.attributes().isEmpty());
    }

    @Test
    void doesNotRemoveDifferentAttributeInstanceWithSameAzName() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        VAttribute stored = attribute("Name");
        VAttribute different = new VAttribute("Name", "Other name", DataType.TEXT, VERSION);
        entity.addAttribute(stored);

        assertFalse(entity.removeAttribute(different));
        assertEquals(List.of(stored), entity.attributes());
    }

    @Test
    void attributesReturnsReadOnlyCopy() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        entity.addAttribute(attribute("Name"));

        List<VAttribute> attributes = entity.attributes();

        assertThrows(UnsupportedOperationException.class, () -> attributes.add(attribute("Email")));
    }

    @Test
    void attributesReturnsSnapshotCopy() {
        VEntity entity = new VEntity("Customer", "Customer", VERSION);
        VAttribute first = attribute("Name");
        VAttribute second = attribute("Email");
        entity.addAttribute(first);

        List<VAttribute> attributes = entity.attributes();
        entity.addAttribute(second);

        assertEquals(List.of(first), attributes);
        assertEquals(List.of(first, second), entity.attributes());
    }

    @Test
    void rejectsInvalidLifecycleVersions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VEntity("Customer", "Customer", new ModelVersion(2, 0, 0), new ModelVersion(1, 0, 0))
        );
    }

    private static VAttribute attribute(String azName) {
        return new VAttribute(azName, azName, DataType.TEXT, VERSION);
    }
}
