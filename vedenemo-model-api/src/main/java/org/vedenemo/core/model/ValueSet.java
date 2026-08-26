package org.vedenemo.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ValueSet {

    private final String azName;
    private final DataType type;
    private final List<ValueSetEntry> entries;

    public ValueSet(String azName, DataType type, List<ValueSetEntry> entries) {
        this.azName = ModelTextRules.requireAzName(azName);
        this.type = requireSupportedType(type);
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("ValueSet entries must not be empty");
        }
        ArrayList<ValueSetEntry> normalized = new ArrayList<>();
        for (ValueSetEntry entry : entries) {
            ValueSetEntry normalizedEntry = new ValueSetEntry(normalizeTechnicalValue(this.type, entry.technicalValue()), entry.visName());
            if (containsTechnicalValue(normalized, this.type, normalizedEntry.technicalValue())) {
                throw new IllegalArgumentException("ValueSet entry technical values must be unique");
            }
            normalized.add(normalizedEntry);
        }
        this.entries = List.copyOf(normalized);
    }

    public static String uniquenessKey(String azName) {
        return ModelTextRules.uniquenessKey(azName);
    }

    public String azName() {
        return azName;
    }

    public DataType type() {
        return type;
    }

    public List<ValueSetEntry> entries() {
        return entries;
    }

    public boolean containsTechnicalValue(Object value) {
        Object normalized = normalizeTechnicalValue(type, value);
        return containsTechnicalValue(entries, type, normalized);
    }

    public static DataType requireSupportedType(DataType type) {
        Objects.requireNonNull(type, "type must not be null");
        if (type == DataType.TEXT || type == DataType.NUMERIC || type == DataType.DATE || type == DataType.TIME) {
            return type;
        }
        throw new IllegalArgumentException("ValueSet type is not supported: " + type);
    }

    public static Object normalizeTechnicalValue(DataType type, Object value) {
        requireSupportedType(type);
        Objects.requireNonNull(value, "technicalValue must not be null");
        return switch (type) {
            case TEXT -> requireString(value, "TEXT");
            case NUMERIC -> numericValue(value);
            case DATE -> dateValue(value);
            case TIME -> timeValue(value);
            case URL, DATA, DATETIME, LOCATION -> throw new IllegalArgumentException("ValueSet type is not supported: " + type);
        };
    }

    private static boolean containsTechnicalValue(List<ValueSetEntry> entries, DataType type, Object value) {
        for (ValueSetEntry entry : entries) {
            if (sameTechnicalValue(type, entry.technicalValue(), value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameTechnicalValue(DataType type, Object left, Object right) {
        if (type == DataType.NUMERIC) {
            return ((BigDecimal) left).compareTo((BigDecimal) right) == 0;
        }
        return left.equals(right);
    }

    private static String requireString(Object value, String label) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(label + " ValueSet entry value must be a string");
        }
        return text;
    }

    private static BigDecimal numericValue(Object value) {
        try {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (value instanceof String text) {
                return new BigDecimal(text);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("NUMERIC ValueSet entry value must be numeric", exception);
        }
        throw new IllegalArgumentException("NUMERIC ValueSet entry value must be numeric");
    }

    private static String dateValue(Object value) {
        String text = requireString(value, "DATE");
        try {
            LocalDate.parse(text);
            return text;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("DATE ValueSet entry value must be an ISO date", exception);
        }
    }

    private static String timeValue(Object value) {
        String text = requireString(value, "TIME");
        try {
            LocalTime.parse(text);
            return text;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("TIME ValueSet entry value must be an ISO time", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValueSet that)) {
            return false;
        }
        return azName.equals(that.azName)
                && type == that.type
                && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(azName, type, entries);
    }
}
