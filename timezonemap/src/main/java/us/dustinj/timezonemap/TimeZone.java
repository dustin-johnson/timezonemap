package us.dustinj.timezonemap;

import java.util.Objects;

import com.esri.core.geometry.Polygon;

@SuppressWarnings("WeakerAccess")
public final class TimeZone {
    private final String zoneId;
    private final Polygon region;

    TimeZone(String zoneId, Polygon region) {
        this.zoneId = zoneId;
        this.region = region;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Polygon getRegion() {
        return region;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        TimeZone timeZone = (TimeZone) o;
        return Objects.equals(getZoneId(), timeZone.getZoneId()) &&
                Objects.equals(getRegion(), timeZone.getRegion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getZoneId(), getRegion());
    }
}
