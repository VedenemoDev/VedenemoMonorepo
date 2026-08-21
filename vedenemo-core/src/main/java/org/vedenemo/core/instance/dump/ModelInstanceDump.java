package org.vedenemo.core.instance.dump;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ModelInstanceDump(
        String format,
        int formatVersion,
        Instant savedAt,
        DumpModel model,
        DumpRoot root,
        List<DumpEntityGroup> entities,
        List<DumpAssociationLink> links
) {
    public static final String FORMAT = "vedenemo-instance-dump";
    public static final int FORMAT_VERSION = 1;

    public ModelInstanceDump {
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("unsupported dump format");
        }
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported dump format version");
        }
        Objects.requireNonNull(savedAt, "savedAt must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(root, "root must not be null");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities must not be null"));
        links = List.copyOf(Objects.requireNonNull(links, "links must not be null"));
    }
}
