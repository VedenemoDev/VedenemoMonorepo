package org.vedenemo.core.instance;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LocationAreaValue(List<LocationValue> boundary) {

    public LocationAreaValue {
        Objects.requireNonNull(boundary, "boundary must not be null");
        if (boundary.size() < 3) {
            throw new IllegalArgumentException("LOCATION_AREA boundary must contain at least three locations");
        }
        boundary.forEach(location -> Objects.requireNonNull(location, "LOCATION_AREA boundary must not contain nulls"));
        if (boundary.getFirst().equals(boundary.getLast())) {
            throw new IllegalArgumentException("LOCATION_AREA boundary must not repeat the first location as the final location");
        }
        Set<LocationValue> distinct = new HashSet<>(boundary);
        if (distinct.size() < 3) {
            throw new IllegalArgumentException("LOCATION_AREA boundary must contain at least three distinct locations");
        }
        boundary = List.copyOf(boundary);
    }
}
