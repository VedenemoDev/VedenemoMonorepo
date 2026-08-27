package org.vedenemo.core.instance;

import java.util.List;
import java.util.Objects;

public record LocationLineValue(List<LocationValue> locations) {

    public LocationLineValue {
        Objects.requireNonNull(locations, "locations must not be null");
        if (locations.size() < 2) {
            throw new IllegalArgumentException("LOCATION_LINE must contain at least two locations");
        }
        locations.forEach(location -> Objects.requireNonNull(location, "LOCATION_LINE locations must not contain nulls"));
        locations = List.copyOf(locations);
    }
}
