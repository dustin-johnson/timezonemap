package us.dustinj.timezonemap.serialization;

import java.util.List;
import java.util.Objects;

public final class TimeZone {
    private final String timeZoneId;
    private final List<LatLon> region;

    public TimeZone(String timeZoneId, List<LatLon> region) {
        this.timeZoneId = timeZoneId;
        this.region = region;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public List<LatLon> getRegion() {
        return region;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        TimeZone timeZone = (TimeZone) o;
        return Objects.equals(getTimeZoneId(), timeZone.getTimeZoneId()) &&
                Objects.equals(getRegion(), timeZone.getRegion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimeZoneId(), getRegion());
    }
}
