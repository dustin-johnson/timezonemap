package us.dustinj.timezonemap;

import com.esri.core.geometry.Geometry;

class TimeZone {
    final String zoneId;
    final Geometry region;

    TimeZone(String zoneId, Geometry region) {
        this.zoneId = zoneId;
        this.region = region;
    }
}
