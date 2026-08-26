package org.vedenemo.core.instance;

import org.vedenemo.core.model.DataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

public record InstanceValue(DataType type, Object value) {

    public InstanceValue {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (type == DataType.NUMERIC && !(value instanceof BigDecimal)) {
            throw new IllegalArgumentException("NUMERIC instance value must be a BigDecimal");
        }
        if ((type == DataType.TEXT
                || type == DataType.URL
                || type == DataType.DATA
                || type == DataType.DATE
                || type == DataType.TIME
                || type == DataType.DATETIME) && !(value instanceof String)) {
            throw new IllegalArgumentException(type + " instance value must be a String");
        }
        if (type == DataType.LOCATION && !(value instanceof LocationValue)) {
            throw new IllegalArgumentException("LOCATION instance value must be a LocationValue");
        }
    }

    public boolean matches(InstanceValue other) {
        Objects.requireNonNull(other, "other must not be null");
        if (type != other.type) {
            return false;
        }
        if (type == DataType.NUMERIC) {
            return ((BigDecimal) value).compareTo((BigDecimal) other.value) == 0;
        }
        if (type == DataType.DATE) {
            return LocalDate.parse((String) value).equals(LocalDate.parse((String) other.value));
        }
        if (type == DataType.TIME) {
            return LocalTime.parse((String) value).equals(LocalTime.parse((String) other.value));
        }
        if (type == DataType.DATETIME) {
            return LocalDateTime.parse((String) value).equals(LocalDateTime.parse((String) other.value));
        }
        return value.equals(other.value);
    }
}
