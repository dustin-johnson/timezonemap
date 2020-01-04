@file:JvmName("Util")

package us.dustinj.timezonemap

import com.esri.core.geometry.Geometry
import com.esri.core.geometry.GeometryEngine
import com.esri.core.geometry.Polygon
import com.esri.core.geometry.SpatialReference

val SPATIAL_REFERENCE = SpatialReference.create("WGS84_WKID")!!

fun containsInclusive(outer: Geometry, inner: Geometry) = GeometryEngine.contains(outer, inner, SPATIAL_REFERENCE) ||
        GeometryEngine.touches(outer, inner, SPATIAL_REFERENCE)

fun convertToEsriBackedTimeZone(timeZone: us.dustinj.timezonemap.serialization.TimeZone): TimeZone {
    val newPolygon = Polygon()

    for (region in timeZone.regions.flatten()) {
        newPolygon.startPath(region[0].longitude.toDouble(), region[0].latitude.toDouble())
        region.subList(1, region.size)
                .forEach { newPolygon.lineTo(it.longitude.toDouble(), it.latitude.toDouble()) }
    }

    return TimeZone(timeZone.timeZoneId, newPolygon)
}
