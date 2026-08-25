package org.vedenemo.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class VAttributeTest {

    private static final ModelVersion VERSION = new ModelVersion(1, 0, 0);

    @Test
    void acceptsValidData() {
        VAttribute attribute = new VAttribute("Display_name", "Display name", DataType.TEXT, VERSION);

        assertEquals("Display_name", attribute.azName());
        assertEquals("Display name", attribute.visName());
        assertEquals(DataType.TEXT, attribute.type());
        assertEquals(VERSION, attribute.activeSince());
        assertEquals(Optional.empty(), attribute.retiredSince());
    }

    @Test
    void acceptsRetiredSinceMetadata() {
        ModelVersion retiredSince = new ModelVersion(3, 0, 0);
        VAttribute attribute = new VAttribute("Display_name", "Display name", DataType.TEXT, VERSION, null, retiredSince);

        assertEquals(Optional.of(retiredSince), attribute.retiredSince());
    }

    @Test
    void rejectsInvalidAzName() {
        assertThrows(IllegalArgumentException.class, () -> new VAttribute("1invalid", "Display name", DataType.TEXT, VERSION));
    }

    @Test
    void rejectsBlankVisName() {
        assertThrows(IllegalArgumentException.class, () -> new VAttribute("Valid", " ", DataType.TEXT, VERSION));
    }

    @Test
    void rejectsMissingDataType() {
        assertThrows(NullPointerException.class, () -> new VAttribute("Valid", "Display name", null, VERSION));
    }

    @Test
    void rejectsMissingActiveSince() {
        assertThrows(NullPointerException.class, () -> new VAttribute("Valid", "Display name", DataType.TEXT, null));
    }

    @Test
    void rejectsDeprecatedSinceEqualToActiveSince() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VAttribute("Valid", "Display name", DataType.TEXT, VERSION, VERSION)
        );
    }

    @Test
    void rejectsDeprecatedSinceBeforeActiveSince() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VAttribute(
                        "Valid",
                        "Display name",
                        DataType.TEXT,
                        new ModelVersion(2, 0, 0),
                        new ModelVersion(1, 9, 9)
                )
        );
    }
}
