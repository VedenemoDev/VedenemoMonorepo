package org.vedenemo.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelRootTest {

    @Test
    void preservesEntityInsertionOrder() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        VEntity first = entity("Customer");
        VEntity second = entity("Order");

        modelRoot.addEntity(first);
        modelRoot.addEntity(second);

        assertEquals(List.of(first, second), modelRoot.entities());
    }

    @Test
    void rejectsDuplicateEntityAzNameCaseInsensitively() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        modelRoot.addEntity(entity("Customer"));

        assertThrows(IllegalArgumentException.class, () -> modelRoot.addEntity(entity("customer")));
    }

    @Test
    void acceptsDigitsAfterFirstLetterInAzName() {
        ModelRoot modelRoot = ModelRoot.create("Example_2026", "Example Model", "1.0.0");
        VEntity entity = new VEntity("Customer2", "Customer", modelRoot.version());
        VAttribute attribute = new VAttribute("Address_Line_1", "Address Line 1", DataType.TEXT, modelRoot.version());

        entity.addAttribute(attribute);
        modelRoot.addEntity(entity);

        assertEquals("Example_2026", modelRoot.azName());
        assertEquals("Customer2", modelRoot.entities().getFirst().azName());
        assertEquals("Address_Line_1", modelRoot.entities().getFirst().attributes().getFirst().azName());
    }

    @Test
    void rejectsAzNameStartingWithDigit() {
        assertThrows(IllegalArgumentException.class, () -> ModelRoot.create("2Example", "Example Model", "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new VEntity("2Customer", "Customer", ModelVersion.parse("1.0.0")));
        assertThrows(IllegalArgumentException.class, () -> new VAttribute("2Name", "Name", DataType.TEXT, ModelVersion.parse("1.0.0")));
    }

    @Test
    void rejectsHyphenInAzName() {
        assertThrows(IllegalArgumentException.class, () -> ModelRoot.create("Example-Model", "Example Model", "1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new VEntity("Customer-2", "Customer", ModelVersion.parse("1.0.0")));
        assertThrows(IllegalArgumentException.class, () -> new VAttribute("Address-Line", "Address Line", DataType.TEXT, ModelVersion.parse("1.0.0")));
    }

    @Test
    void removesEntityByAzName() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        VEntity entity = entity("Customer");
        modelRoot.addEntity(entity);

        assertEquals(entity, modelRoot.removeEntity("customer").orElseThrow());
        assertTrue(modelRoot.entities().isEmpty());
    }

    @Test
    void removesEntityByInstance() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        VEntity entity = entity("Customer");
        modelRoot.addEntity(entity);

        assertTrue(modelRoot.removeEntity(entity));
        assertTrue(modelRoot.entities().isEmpty());
    }

    @Test
    void doesNotRemoveDifferentEntityInstanceWithSameAzName() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        VEntity stored = entity("Customer");
        VEntity different = new VEntity("Customer", "Different Customer", ModelVersion.parse("1.0.0"));
        modelRoot.addEntity(stored);

        assertFalse(modelRoot.removeEntity(different));
        assertEquals(List.of(stored), modelRoot.entities());
    }

    @Test
    void entitiesReturnsReadOnlySnapshotCopy() {
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "1.0.0");
        VEntity first = entity("Customer");
        VEntity second = entity("Order");
        modelRoot.addEntity(first);

        List<VEntity> entities = modelRoot.entities();
        modelRoot.addEntity(second);

        assertThrows(UnsupportedOperationException.class, () -> entities.add(entity("Product")));
        assertEquals(List.of(first), entities);
        assertEquals(List.of(first, second), modelRoot.entities());
    }

    private static VEntity entity(String azName) {
        return new VEntity(azName, azName, ModelVersion.parse("1.0.0"));
    }
}
