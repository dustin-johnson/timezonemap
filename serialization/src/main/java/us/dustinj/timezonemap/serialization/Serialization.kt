@file:JvmName("Serialization")

package us.dustinj.timezonemap.serialization

import com.google.flatbuffers.FlatBufferBuilder
import us.dustinj.timezonemap.serialization.flatbuffer.Point
import us.dustinj.timezonemap.serialization.flatbuffer.Polygon
import us.dustinj.timezonemap.serialization.flatbuffer.Ring
import java.nio.ByteBuffer

data class Envelope(val lowerLeftCorner: LatLon, val upperRightCorner: LatLon)
data class LatLon(val latitude: Float, val longitude: Float)
data class TimeZone(val timeZoneId: String,
        /**
         * A list of polygons, each with multiple rings (where the first ring is the outer boundary and subsequent rings are
         * holes in that area), and each ring is composed of multiple points.
         */
        val regions: List<List<List<LatLon>>>)

fun serializeEnvelope(e: Envelope) = "${e.lowerLeftCorner.latitude},${e.lowerLeftCorner.longitude}," +
        "${e.upperRightCorner.latitude},${e.upperRightCorner.longitude}"

fun deserializeEnvelope(envelope: String) = envelope.split(",")
        .let { Envelope(LatLon(it[0].toFloat(), it[1].toFloat()), LatLon(it[2].toFloat(), it[3].toFloat())) }

fun serializeTimeZone(timeZone: TimeZone): ByteBuffer {
    val builder = FlatBufferBuilder(timeZone.regions.asSequence()
            .map { it.size }
            .sum() * 8 + timeZone.timeZoneId.length * 2 + 256)
    val regionOffsets = timeZone.regions.asSequence()
            .map { serializePolygon(builder, it) }
            .map { i: Int? -> i!! }
            .toList()
            .toIntArray()
    val regionsOffset =
            us.dustinj.timezonemap.serialization.flatbuffer.TimeZone.createRegionsVector(builder, regionOffsets)
    builder.finish(us.dustinj.timezonemap.serialization.flatbuffer.TimeZone.createTimeZone(builder,
            builder.createString(timeZone.timeZoneId), regionsOffset))

    return builder.dataBuffer()
}

fun deserializeTimeZone(serializedTimeZone: ByteBuffer): TimeZone {
    val timeZone = us.dustinj.timezonemap.serialization.flatbuffer.TimeZone.getRootAsTimeZone(serializedTimeZone)
    val regions = (0 until timeZone.regionsLength()).map { deserializePolygon(timeZone.regions(it)) }

    return TimeZone(timeZone.timeZoneName(), regions)
}

private fun serializeRing(builder: FlatBufferBuilder, ring: List<LatLon>): Int {
    Ring.startPointsVector(builder, ring.size)
    // Reverse the list as flat buffers reverses it during serialization and we'd prefer to keep the original order.
    ring.reversed().forEach { Point.createPoint(builder, it.latitude, it.longitude) }

    return Ring.createRing(builder, builder.endVector())
}

private fun serializePolygon(builder: FlatBufferBuilder, polygon: List<List<LatLon>>): Int {
    val regionOffsets = polygon.map { ring -> serializeRing(builder, ring) }
    val regionsOffset = Polygon.createRingsVector(builder, regionOffsets.toIntArray())

    return Polygon.createPolygon(builder, regionsOffset)
}

private fun deserializePolygon(polygon: Polygon): List<List<LatLon>> = (0 until polygon.ringsLength()).asSequence()
        .map { ringIndex -> polygon.rings(ringIndex) }
        .map { ring ->
            (0 until ring.pointsLength())
                    .map { pointIndex -> ring.points(pointIndex) }
                    .map { point -> LatLon(point.latitude(), point.longitude()) }
        }
        .toList()
