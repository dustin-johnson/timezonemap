package us.dustinj.timezonemap.serialization;

import java.util.Objects;

public final class LatLon {
    private final double latitude;
    private final double longitude;

    public LatLon(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        LatLon latLon = (LatLon) o;
        return Double.compare(latLon.getLatitude(), getLatitude()) == 0 &&
                Double.compare(latLon.getLongitude(), getLongitude()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLatitude(), getLongitude());
    }
}
