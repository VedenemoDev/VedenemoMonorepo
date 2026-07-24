package org.vedenemo.core.model;

import java.util.Objects;
import java.util.OptionalInt;

public final class Cardinality {

    private static final UpperBound UNBOUNDED = new UpperBound(true, 0);

    private final int lowerBound;
    private final UpperBound upperBound;

    private Cardinality(int lowerBound, UpperBound upperBound) {
        if (lowerBound < 0) {
            throw new IllegalArgumentException("cardinality lower bound must not be negative");
        }
        this.lowerBound = lowerBound;
        this.upperBound = Objects.requireNonNull(upperBound, "upperBound must not be null");
        if (!upperBound.unbounded() && upperBound.value() < lowerBound) {
            throw new IllegalArgumentException("cardinality upper bound must not be lower than lower bound");
        }
        if (lowerBound == 0 && !upperBound.unbounded() && upperBound.value() == 0) {
            throw new IllegalArgumentException("cardinality 0..0 is not meaningful");
        }
    }

    public static Cardinality of(int exactBound) {
        return bounded(exactBound, exactBound);
    }

    public static Cardinality bounded(int lowerBound, int upperBound) {
        return new Cardinality(lowerBound, new UpperBound(false, upperBound));
    }

    public static Cardinality unbounded(int lowerBound) {
        return new Cardinality(lowerBound, UNBOUNDED);
    }

    public static Cardinality parse(String text) {
        Objects.requireNonNull(text, "cardinality must not be null");
        String value = text.trim();
        if (value.equals("*")) {
            return unbounded(0);
        }
        int rangeSeparator = value.indexOf("..");
        if (rangeSeparator >= 0) {
            if (rangeSeparator != value.lastIndexOf("..")) {
                throw new IllegalArgumentException("cardinality range must contain one '..' separator");
            }
            String lower = value.substring(0, rangeSeparator);
            String upper = value.substring(rangeSeparator + 2);
            int lowerBound = parseBound(lower, "lower");
            if (upper.equals("*")) {
                return unbounded(lowerBound);
            }
            return bounded(lowerBound, parseBound(upper, "upper"));
        }
        return of(parseBound(value, "exact"));
    }

    public int lowerBound() {
        return lowerBound;
    }

    public OptionalInt upperBound() {
        if (upperBound.unbounded()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(upperBound.value());
    }

    public boolean isUpperUnbounded() {
        return upperBound.unbounded();
    }

    @Override
    public String toString() {
        if (upperBound.unbounded()) {
            return lowerBound + "..*";
        }
        if (lowerBound == upperBound.value()) {
            return Integer.toString(lowerBound);
        }
        return lowerBound + ".." + upperBound.value();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Cardinality that)) {
            return false;
        }
        return lowerBound == that.lowerBound && upperBound.equals(that.upperBound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound);
    }

    private static int parseBound(String text, String boundName) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("cardinality " + boundName + " bound must not be blank");
        }
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value < '0' || value > '9') {
                throw new IllegalArgumentException("cardinality " + boundName + " bound must be a non-negative integer");
            }
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cardinality " + boundName + " bound is too large", exception);
        }
    }

    private record UpperBound(boolean unbounded, int value) {
    }
}
