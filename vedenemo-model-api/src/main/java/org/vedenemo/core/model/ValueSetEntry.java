package org.vedenemo.core.model;

import java.util.Objects;

public record ValueSetEntry(Object technicalValue, String visName) {

    public ValueSetEntry {
        technicalValue = Objects.requireNonNull(technicalValue, "technicalValue must not be null");
        visName = ModelTextRules.requireVisName(visName);
    }
}
