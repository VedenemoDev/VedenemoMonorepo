package org.vedenemo.core.instance.dump;

import java.util.List;

public record DumpEntityGroup(String entityAzName, List<DumpEntityRecord> records) {

    public DumpEntityGroup {
        entityAzName = requireText(entityAzName, "entity azName");
        records = List.copyOf(records == null ? List.of() : records);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
