package us.dustinj.timezonemap.serialization;

import java.util.Objects;

public final class LatLon {
    private final float latitude;
    private final float longitude;

    public LatLon(float latitude, float longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        LatLon latLon = (LatLon) o;
        return Float.compare(latLon.getLatitude(), getLatitude()) == 0 &&
                Float.compare(latLon.getLongitude(), getLongitude()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLatitude(), getLongitude());
    }
}
