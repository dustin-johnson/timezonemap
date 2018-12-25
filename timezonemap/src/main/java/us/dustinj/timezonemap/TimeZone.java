package us.dustinj.timezonemap;

import java.util.Objects;

import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;

/**
 * The time zone identifier (e.g. {@code America/Los_Angeles}, {@code Europe/Berlin}, {@code Etc/GMT+5},
 * {@code Asia/Shanghai}) and the region on Earth that the time zone covers.
 */
@SuppressWarnings("WeakerAccess")
public final class TimeZone {
    private final String zoneId;
    private final Polygon region;

    TimeZone(String zoneId, Polygon region) {
        this.zoneId = zoneId;
        this.region = region;
    }

    /**
     * @return The identifier of the time zone that can be used, in modern java versions, to initialize the
     *         {@code java.util.TimeZone} object and interact with the time zone programmatically. Examples:
     *         {@code America/Los_Angeles}, {@code Europe/Berlin}, {@code Etc/GMT+5}, {@code Asia/Shanghai}.
     */
    public String getZoneId() {
        return zoneId;
    }

    /**
     * @return The region of the Earth this time zone covers. Note, is the {@link TimeZoneMap} was initializes with
     *         {@link TimeZoneMap#forRegion(double, double, double, double)}, then this region will be clipped to the
     *         region supplied at initialization.
     */
    public Polygon getRegion() {
        return region;
    }

    /**
     * Calculate the minimum distance (in meters) that would need to be traveled from the provided location to no
     * longer be in this time zone. Note, if this region comes from a map that was initialized with
     * {@link TimeZoneMap#forRegion(double, double, double, double)}, then this distance could represent the distance
     * to the boundary provided during initialization, if that boundary is the closest way of exiting this time
     * zone's region.
     *
     * @param degreesLatitude
     *         90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude
     *         180.0 to -180.0 such that positive is East, negative is West, and the White House of the United States
     *         is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     * @return The minimum distance (in meters) that would need to be traveled from the provided location to no longer
     *         be in this time zone. Note, if this region comes from a map that was initialized with
     *         {@link TimeZoneMap#forRegion(double, double, double, double)}, then this distance could represent the
     *         distance to the boundary provided during initialization, if that boundary is the closest way of exiting
     *         this time zone's region.
     * @throws IllegalArgumentException
     *         If the provided location is not within this time zone, inclusive of the edge.
     */
    public double getDistanceFromBoundary(double degreesLatitude, double degreesLongitude) {
        Point location = new Point(degreesLongitude, degreesLatitude);
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
