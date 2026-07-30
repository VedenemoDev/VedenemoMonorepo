package org.vedenemo.core.instance;

import org.vedenemo.core.model.DataType;

import java.math.BigDecimal;
import java.util.Objects;

public record InstanceValue(DataType type, Object value) {

    public InstanceValue {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (type == DataType.NUMERIC && !(value instanceof BigDecimal)) {
            throw new IllegalArgumentException("NUMERIC instance value must be a BigDecimal");
        }
        if ((type == DataType.TEXT || type == DataType.URL || type == DataType.DATA) && !(value instanceof String)) {
            throw new IllegalArgumentException(type + " instance value must be a String");
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
        return value.equals(other.value);
    }
}
