package org.vedenemo.storage.memory;

import org.junit.jupiter.api.Test;
import org.vedenemo.core.model.ModelRoot;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InMemoryModelStorageTest {

    @Test
    void savesAndLoadsModelRoot() {
        InMemoryModelStorage storage = new InMemoryModelStorage();
        ModelRoot modelRoot = ModelRoot.create("Example_Model", "Example Model", "01.2.003");

        storage.save("example", modelRoot);

        Optional<ModelRoot> loaded = storage.load("example");
        assertTrue(loaded.isPresent());
        assertEquals(modelRoot, loaded.orElseThrow());
        assertEquals("1.2.3", loaded.orElseThrow().version().toString());
    }

    @Test
    void rejectsBlankModelId() {
        InMemoryModelStorage storage = new InMemoryModelStorage();
        ModelRoot modelRoot = ModelRoot.create("Example", "Example Model", "1.0.0");

        assertThrows(IllegalArgumentException.class, () -> storage.save(" ", modelRoot));
        assertThrows(IllegalArgumentException.class, () -> storage.load(" "));
    }

    @Test
    void rejectsNullModelRoot() {
        InMemoryModelStorage storage = new InMemoryModelStorage();

        assertThrows(NullPointerException.class, () -> storage.save("example", null));
    }
}
