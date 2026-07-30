package org.vedenemo.core.instance;

import java.util.Locale;

public enum RelationshipDirection {
    OUTGOING,
    INCOMING,
    EITHER;

    public static RelationshipDirection parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "outgoing" -> OUTGOING;
            case "incoming" -> INCOMING;
            case "either" -> EITHER;
            default -> throw new IllegalArgumentException("unsupported relationship direction: " + value);
        };
    }
}
