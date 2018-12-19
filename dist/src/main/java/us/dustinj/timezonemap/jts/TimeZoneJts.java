package us.dustinj.timezonemap.jts;

import java.util.Objects;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;

import com.esri.core.geometry.GeometryEngine;

final class TimeZoneJts {
    private final String zoneId;
    private final Geometry region;

    TimeZoneJts(String zoneId, Geometry region) {
        this.zoneId = zoneId;
        this.region = region;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Geometry getRegion() {
        return region;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        TimeZoneJts timeZone = (TimeZoneJts) o;
        return Objects.equals(getZoneId(), timeZone.getZoneId()) &&
                Objects.equals(getRegion(), timeZone.getRegion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getZoneId(), getRegion());
    }
}
