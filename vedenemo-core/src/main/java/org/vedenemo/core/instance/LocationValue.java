package org.vedenemo.core.instance;

public record LocationValue(double latitude, double longitude) {

    public LocationValue {
        latitude = requireCoordinate(latitude, -90.0, 90.0, "latitude");
        longitude = requireCoordinate(longitude, -180.0, 180.0, "longitude");
    }

    private static double requireCoordinate(double value, double min, double max, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("LOCATION " + name + " must be finite");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("LOCATION " + name + " must be between " + min + " and " + max);
        }
        if (value == 0.0d) {
            return 0.0d;
        }
        return value;
    }
}
