package us.dustinj.timezonemap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.SpatialReference;

import us.dustinj.timezonemap.serialization.LatLon;

final class Util {
    static final SpatialReference SPATIAL_REFERENCE = SpatialReference.create("WGS84_WKID");

    // Utility class
    private Util() {}

    static boolean containsInclusive(Geometry outer, Geometry inner) {
        return GeometryEngine.contains(outer, inner, SPATIAL_REFERENCE) ||
                GeometryEngine.touches(outer, inner, SPATIAL_REFERENCE);
    }

    static TimeZone convertToEsriBackedTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        Polygon newPolygon = new Polygon();

        List<List<LatLon>> list = new ArrayList<>();
        for (List<List<LatLon>> subList : timeZone.getRegions())
            list.addAll(subList);
        for (List<LatLon> region : list) {
            newPolygon.startPath(region.get(0).getLongitude(), region.get(0).getLatitude());
            region.subList(1, region.size())
                    .forEach(p -> newPolygon.lineTo(p.getLongitude(), p.getLatitude()));
        }

        return new TimeZone(timeZone.getTimeZoneId(), newPolygon);
    }
}
