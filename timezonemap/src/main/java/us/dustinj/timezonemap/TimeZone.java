package us.dustinj.timezonemap;

import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Point2D;
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

    public double getDistanceFromBoundary(double latitude, double longitude) {
        Point location = new Point(longitude, latitude);
        Util.precondition(Util.containsInclusive(this.region, location), "Location must be inside the time zone");

        return GeometryEngine.geodesicDistanceOnWGS84(location,
                GeometryEngine.getNearestCoordinate(this.getRegion(), location, false).getCoordinate());
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
