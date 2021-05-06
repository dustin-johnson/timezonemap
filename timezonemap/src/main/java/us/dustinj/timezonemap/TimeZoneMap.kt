package us.dustinj.timezonemap

import com.esri.core.geometry.Envelope2D
import com.esri.core.geometry.OperatorIntersection
import com.esri.core.geometry.Point
import com.esri.core.geometry.Polygon
import com.esri.core.geometry.SimpleGeometryCursor
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import us.dustinj.timezonemap.TimeZoneMap.Companion.forRegion
import us.dustinj.timezonemap.data.getDataInputStream
import us.dustinj.timezonemap.serialization.deserializeEnvelope
import us.dustinj.timezonemap.serialization.deserializeTimeZone
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class TimeZoneMap private constructor(
        /**
         * The version consists of two parts separated by a colon. The first part is the version of this map library,
         * and the second part is the version of the map shapes. Example: 3.1:2018i
         */
        val mapVersion: String?,
        /**
         * A list of all time zone identifiers, and their regions, contained in this index. The list is sorted by the area
         * the time zone covers, smallest first. Note, the area computation used for sorting considers the real-world area
         * the time zone covers regardless of the region the index was initialized for. If this index was initialized using
         * [forRegion], then the returned list represents all time zones that overlap with the coordinates with which this
         * index was initialized and the regions are clipped to the initialization coordinates.
         *
         * This list represents the full range of time zones that can be returned by [getOverlappingTimeZone] or
         * [getOverlappingTimeZones].
         */
        val timeZones: List<TimeZone>,
        /**
         * The region (inclusive of the boundary) for which this map was initialized. Only locations with in this region
         * may be queried using this map instance.
         */
        val initializedRegion: Envelope2D) {

    /**
     * Retrieve the time zone in use at the provided coordinates. The identifier contained in this time zone can be
     * used, in modern Java versions, to initialize the `java.util.TimeZone` object and interact with the time zone
     * programmatically.
     *
     * @param degreesLatitude 90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude 180.0 to -180.0 such that positive is East, negative is West, and the White House of the
     * United States is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     * @return A time zone that is in use at the provided coordinates, if such a time zone exists. If multiple time
     * zones overlap the provided coordinates, as can happen in disputed areas such as the South China Sea, then the
     * time zone with the smallest land area will be provided. If this index was initialized using [forRegion], then the
     * returned time zone region is clipped to the initialization coordinates.
     * @throws IllegalArgumentException If the provided coordinates are outside of the area indexed by this instance of
     * the time zone index.
     */
    fun getOverlappingTimeZone(degreesLatitude: Double, degreesLongitude: Double): TimeZone? =
            getOverlappingTimeZoneSequence(degreesLatitude, degreesLongitude).first()

    /**
     * Retrieve all time zones in use at the provided coordinates. Multiple time zones can overlap the provided location
     * in disputed areas such as the South China Sea). The returned time zones are sorted such that the first entry in
     * the list is the time zone with the smallest land area. The identifiers contained in the returned time zones can
     * be used in modern Java versions to initialize the `java.util.TimeZone` object and interact with the time zone
     * programmatically.
     *
     * @param degreesLatitude 90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude 180.0 to -180.0 such that positive is East, negative is West, and the White House of the
     * United States is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     * @return List of time zones that are in use at the provided coordinates, if such time zones exists. If multiple
     * time zones overlap the provided coordinates, as can happen in disputed areas such as the South China Sea, then
     * all overlapping time zones are returned, sorted such that the zone with the smallest land area is first in the
     * list. If no time zones overlap the provided coordinates, then an empty list will be returned. If this index was
     * initialized using [forRegion], then the regions are clipped to the initialization coordinates.
     * @throws IllegalArgumentException If the provided coordinates are outside of the area indexed by this instance of
     * the time zone index.
     */
    fun getOverlappingTimeZones(degreesLatitude: Double, degreesLongitude: Double) =
            getOverlappingTimeZoneSequence(degreesLatitude, degreesLongitude).toList()

    private fun getOverlappingTimeZoneSequence(degreesLatitude: Double, degreesLongitude: Double): Sequence<TimeZone> {
        val point = Point(degreesLongitude, degreesLatitude)
        require(initializedRegion.contains(point.xy)) { "Requested point is outside the initialized area" }

        return timeZones.asSequence().filter { containsInclusive(it.region, point) }
    }

    private class ExtentsAndTimeZone(val extents: Envelope2D, val timeZone: TimeZone)

    companion object {
        /**
         * Creates a new instance of [TimeZoneMap] and initializes it to be valid for the entire world. This is a
         * blocking long running operation that takes about 1-2 seconds on desktop hardware. If performance is a
         * concern, consider using [forRegion].
         *
         * @return A map instance that can be used for querying locations anywhere in the world.
         */
        @JvmStatic
        fun forEverywhere() = forRegion(-90.0, -180.0, 90.0, 180.0)

        /**
         * Creates a new instance of [TimeZoneMap] using the default map data and initializes it to be valid for
         * anywhere within the provided coordinates (inclusive). This is a blocking long-running operation that can be
         * very quick depending on the area described by the provided coordinates. Initializing the map this way not
         * only improves speed, but also reduces memory usage, as only the regions withing the provided coordinates are
         * held in memory. Large time zone regions are clipped so that only the pieces within the provided coordinates
         * are kept in memory.
         *
         * @param minDegreesLatitude Between -90.0 and 90.0, which are the South and North pole respectively. This value
         * is the southern most boundary to be indexed, inclusive.
         * @param minDegreesLongitude Between -180.0 and 180.0, which are farthest West and East respectively. The White
         * House of the United States is at -77.036586 degrees longitude. This value is the western most boundary to be
         * indexed, inclusive.
         * @param maxDegreesLatitude Between -90.0 and 90.0, which are the South and North pole respectively. This value
         * is the northern most boundary to be indexed, inclusive.
         * @param maxDegreesLongitude Between -180.0 and 180.0, which are farthest West and East respectively. The White
         * House of the United States is at -77.036586 degrees longitude. This value is the eastern most boundary to be
         * indexed, inclusive.
         * @return A map instance that can be used for querying locations withing the provided coordinates, inclusive.
         * @throws IllegalArgumentException If minimum values aren't less than maximum values.
         */
        @JvmStatic
        fun forRegion(minDegreesLatitude: Double, minDegreesLongitude: Double,
                maxDegreesLatitude: Double, maxDegreesLongitude: Double): TimeZoneMap {
            try {
                getDataInputStream().use { inputStream ->
                    return forRegion(inputStream, minDegreesLatitude, minDegreesLongitude,
                            maxDegreesLatitude, maxDegreesLongitude)
                }
            } catch (e: IOException) {
                throw IllegalStateException("Unable to read time zone data resource file", e)
            }
        }

        /**
         * Creates a new instance of [TimeZoneMap] and initializes it to be valid for anywhere within the provided
         * coordinates (inclusive). This is a blocking long-running operation that can be very quick depending on the
         * area described by the provided coordinates. Initializing the map this way not only improves speed, but also
         * reduces memory usage, as only the regions withing the provided coordinates are held in memory. Large time
         * zone regions are clipped so that only the pieces within the provided coordinates are kept in memory.
         *
         * @param tarInputStream A stream containing the tar archive. Any compression or other packaging must have
         * already been unwrapped before this.
         * @param minDegreesLatitude Between -90.0 and 90.0, which are the South and North pole respectively. This value
         * is the southern most boundary to be indexed, inclusive.
         * @param minDegreesLongitude Between -180.0 and 180.0, which are farthest West and East respectively. The White
         * House of the United States is at -77.036586 degrees longitude. This value is the western most boundary to be
         * indexed, inclusive.
         * @param maxDegreesLatitude Between -90.0 and 90.0, which are the South and North pole respectively. This value
         * is the northern most boundary to be indexed, inclusive.
         * @param maxDegreesLongitude Between -180.0 and 180.0, which are farthest West and East respectively. The White
         * House of the United States is at -77.036586 degrees longitude. This value is the eastern most boundary to be
         * indexed, inclusive.
         * @return A map instance that can be used for querying locations withing the provided coordinates, inclusive.
         * @throws IllegalArgumentException If minimum values aren't less than maximum values.
         */
        @JvmStatic
        fun forRegion(tarInputStream: InputStream? = null,
                minDegreesLatitude: Double, minDegreesLongitude: Double,
                maxDegreesLatitude: Double, maxDegreesLongitude: Double): TimeZoneMap {
            require(minDegreesLatitude < maxDegreesLatitude) { "Minimum latitude must be less than maximum latitude" }
            require(minDegreesLongitude < maxDegreesLongitude) { "Minimum longitude must be less than maximum longitude" }

            val indexAreaEnvelope =
                    Envelope2D(minDegreesLongitude, minDegreesLatitude, maxDegreesLongitude, maxDegreesLatitude)
            val indexAreaPolygon = envelopeToPolygon(indexAreaEnvelope)

            try {
                TarArchiveInputStream(tarInputStream).use { archiveInputStream ->
                    var mapVersion: String? = null
                    val timeZones = getTarEntrySequence(archiveInputStream)
                            .onEach { entry: TarArchiveEntry ->
                                if (mapVersion == null) {
                                    val splitVersion = entry.name.split(" ").toTypedArray()
                                    val version = if (splitVersion.size == 2) splitVersion[1] else entry.name
                                    require(version.split(":")[0] == BuildInformation.VERSION) {
                                        "Incompatible map archive. Detected version is '$version' required version " +
                                                "'${BuildInformation.VERSION}:*'"
                                    }

                                    mapVersion = version
                                }
                            }
                            .filter { it.size > 0 }
                            // The name of each file is an envelope that is the outside boundary of the time zone. This
                            // allows us to immediately filter out any time zones that don't overlap the initialization
                            // region without having to deserialize the region, which is a fairly expensive operation.
                            .filter { entry: TarArchiveEntry ->
                                val fragmentedName = entry.name.split("/").toTypedArray()
                                val serializedEnvelope = fragmentedName[fragmentedName.size - 1]
                                val envelope = deserializeEnvelope(serializedEnvelope)
                                indexAreaEnvelope.isIntersecting(
                                        envelope.lowerLeftCorner.longitude.toDouble(),
                                        envelope.lowerLeftCorner.latitude.toDouble(),
                                        envelope.upperRightCorner.longitude.toDouble(),
                                        envelope.upperRightCorner.latitude.toDouble())
                            }
                            .map { entry: TarArchiveEntry ->
                                ByteBuffer.wrap(ByteArray(entry.size.toInt())).apply {
                                    var readLength: Int
                                    while (archiveInputStream.read(array(), position(),
                                                    remaining()).also { readLength = it } > 0) {
                                        position(position() + readLength)
                                    }
                                    position(0)
                                }
                            }
                            .map { deserializeTimeZone(it) }
                            .map { convertToEsriBackedTimeZone(it) }
                            .map { timeZone: TimeZone ->
                                val extents = Envelope2D()
                                timeZone.region.queryEnvelope2D(extents)
                                ExtentsAndTimeZone(extents, timeZone)
                            }
                            // Throw out anything that doesn't at least partially overlap with the index area.
                            .filter { indexAreaEnvelope.isIntersecting(it.extents) }
                            // Sort smallest area first so we have a deterministic ordering of there is an overlap.
                            .sortedBy { it.timeZone.region.calculateArea2D() }
                            // Clip the shape to our indexArea so we don't have to keep large time zones that may
                            // only slightly intersect with the region we're indexing.
                            .flatMap { t: ExtentsAndTimeZone ->
                                if (indexAreaEnvelope.contains(t.extents)) return@flatMap sequenceOf(t.timeZone)

                                val intersectedGeometries =
                                        OperatorIntersection.local().execute(
                                                SimpleGeometryCursor(t.timeZone.region),
                                                SimpleGeometryCursor(indexAreaPolygon),
                                                SPATIAL_REFERENCE,
                                                null, -1)

                                // Since we're intersecting polygons, the only thing we can get back must be 2 dimensional,
                                // so it's safe to cast everything we get back as a polygon.
                                generateSequence { intersectedGeometries.next() as? Polygon }
                                        .filter { it.pointCount > 0 }
                                        .map { TimeZone(t.timeZone.zoneId, it) }
                            }
                            .toList()

                    return TimeZoneMap(mapVersion, timeZones, indexAreaEnvelope)
                }
            } catch (e: IOException) {
                throw IllegalStateException("Unable to read time zone data resource file", e)
            }
        }

        @JvmStatic
        fun envelopeToPolygon(envelope: Envelope2D) = Polygon().apply {
            startPath(envelope.xmin, envelope.ymax) // Upper left
            lineTo(envelope.xmax, envelope.ymax) // Upper right
            lineTo(envelope.xmax, envelope.ymin) // Lower right
            lineTo(envelope.xmin, envelope.ymin) // Lower left
            lineTo(envelope.xmin, envelope.ymax) // Upper left
        }

        private fun getTarEntrySequence(f: TarArchiveInputStream) = generateSequence {
            try {
                f.nextTarEntry
            } catch (e: IOException) {
                throw IllegalStateException("Unable to read time zone data resource file", e)
            }
        }
    }
}