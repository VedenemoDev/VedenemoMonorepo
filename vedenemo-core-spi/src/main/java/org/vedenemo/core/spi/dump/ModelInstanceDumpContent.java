package org.vedenemo.core.spi.dump;

import java.util.Objects;

public record ModelInstanceDumpContent(ModelInstanceDumpDescriptor descriptor, String content) {

    public ModelInstanceDumpContent {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
