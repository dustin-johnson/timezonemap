package us.dustinj.timezonemap

import com.esri.core.geometry.GeometryEngine
import com.esri.core.geometry.Point
import com.esri.core.geometry.Polygon

/**
 * The time zone identifier (e.g. `America/Los_Angeles`, `Europe/Berlin`, `Etc/GMT+5`, `Asia/Shanghai`) and the region
 * on Earth that the time zone covers.
 */
data class TimeZone(
        /**
         * The identifier of the time zone that can be used, in modern java versions, to initialize the
         * `java.util.TimeZone` object and interact with the time zone programmatically. Examples:
         * `America/Los_Angeles`, `Europe/Berlin`, `Etc/GMT+5`, `Asia/Shanghai`.
         */
        val zoneId: String,
        /**
         * The region of the Earth this time zone covers. Note, if the [TimeZoneMap] was initialized with
         * [TimeZoneMap.forRegion], then this region will be clipped to the region supplied at initialization.
         */
        val region: Polygon) {

    /**
     * Calculate the minimum distance (in meters) that would need to be traveled from the provided location to no longer
     * be in this time zone. Note, if this region comes from a map that was initialized with [TimeZoneMap.forRegion],
     * then this distance could represent the distance to the boundary provided during initialization, if that boundary
     * is the closest way of exiting this time zone's region.
     *
     * @param degreesLatitude 90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude 180.0 to -180.0 such that positive is East, negative is West, and the White House of the
     * United States is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     *
     * @return The minimum distance (in meters) that would need to be traveled from the provided location to no longer
     * be in this time zone. Note, if this region comes from a map that was initialized with [TimeZoneMap.forRegion],
     * then this distance could represent the distance to the boundary provided during initialization, if that boundary
     * is the closest way of exiting this time zone's region.
     *
     * @throws IllegalArgumentException If the provided location is not within this time zone, inclusive of the edge.
     */
    fun getDistanceFromBoundary(degreesLatitude: Double, degreesLongitude: Double): Double {
        val location = Point(degreesLongitude, degreesLatitude)
        require(containsInclusive(region, location)) { "Location must be inside the time zone" }

        return GeometryEngine.geodesicDistanceOnWGS84(location,
                GeometryEngine.getNearestCoordinate(region, location, false).coordinate)
    }
}