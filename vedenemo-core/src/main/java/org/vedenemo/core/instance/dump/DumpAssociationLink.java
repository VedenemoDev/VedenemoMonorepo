package org.vedenemo.core.instance.dump;

public record DumpAssociationLink(String associationAzName, String sourceDumpId, String targetDumpId) {

    public DumpAssociationLink {
        associationAzName = requireText(associationAzName, "association azName");
        sourceDumpId = requireText(sourceDumpId, "source dump id");
        targetDumpId = requireText(targetDumpId, "target dump id");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
