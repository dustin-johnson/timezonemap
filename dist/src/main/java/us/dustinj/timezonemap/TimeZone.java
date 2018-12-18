package us.dustinj.timezonemap;

import com.esri.core.geometry.Polygon;

class TimeZone {
    final String zoneId;
    final Polygon region;

    TimeZone(String zoneId, Polygon region) {
        this.zoneId = zoneId;
        this.region = region;
    }
}
