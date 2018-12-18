package us.dustinj.timezonemap.serialization;

import java.util.List;
import java.util.Objects;

public final class TimeZone {
    private final String timeZoneId;
    private final List<LatLon> exteriorRegion;
    private final List<List<LatLon>> interiorRegions;

    public TimeZone(String timeZoneId, List<LatLon> exteriorRegion,
            List<List<LatLon>> interiorRegions) {
        this.timeZoneId = timeZoneId;
        this.exteriorRegion = exteriorRegion;
        this.interiorRegions = interiorRegions;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public List<LatLon> getExteriorRegion() {
        return exteriorRegion;
    }

    public List<List<LatLon>> getInteriorRegions() {
        return interiorRegions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        TimeZone timeZone = (TimeZone) o;
        return Objects.equals(getTimeZoneId(), timeZone.getTimeZoneId()) &&
                Objects.equals(getExteriorRegion(), timeZone.getExteriorRegion()) &&
                Objects.equals(getInteriorRegions(), timeZone.getInteriorRegions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimeZoneId(), getExteriorRegion(), getInteriorRegions());
    }
}
