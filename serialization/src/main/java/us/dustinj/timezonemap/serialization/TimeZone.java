package us.dustinj.timezonemap.serialization;

import java.util.List;
import java.util.Objects;

public final class TimeZone {
    private final String timeZoneId;
    private final List<List<List<LatLon>>> regions;

    public TimeZone(String timeZoneId, List<List<List<LatLon>>> regions) {
        this.timeZoneId = timeZoneId;
        this.regions = regions;
    }

    public String getTimeZoneId() {
        return this.timeZoneId;
    }

    /**
     * Get the regions covered by this time zone.
     *
     * @return A list of polygons, each with multiple rings (where the first ring is the outer boundary and
     *         subsequent rings are holes in that area), and each ring is composed of multiple points.
     */
    public List<List<List<LatLon>>> getRegions() {
        return this.regions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        TimeZone timeZone = (TimeZone) o;
        return Objects.equals(getTimeZoneId(), timeZone.getTimeZoneId()) &&
                Objects.equals(getRegions(), timeZone.getRegions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimeZoneId(), getRegions());
    }
}
