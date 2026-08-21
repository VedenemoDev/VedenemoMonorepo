package org.vedenemo.core.instance.dump;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DumpEntityRecord(String dumpId, Map<String, Object> values) {

    public DumpEntityRecord {
        dumpId = requireText(dumpId, "dump id");
        values = new LinkedHashMap<>(Objects.requireNonNull(values, "values must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
