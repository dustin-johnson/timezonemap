package us.dustinj.timezonemap.builder

import com.esri.core.geometry.OperatorSimplify
import com.esri.core.geometry.SpatialReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.geojson.LngLatAlt
import org.geojson.MultiPolygon
import org.geojson.Polygon
import us.dustinj.timezonemap.serialization.Envelope
import us.dustinj.timezonemap.serialization.LatLon
import us.dustinj.timezonemap.serialization.TimeZone
import us.dustinj.timezonemap.serialization.serializeEnvelope
import us.dustinj.timezonemap.serialization.serializeTimeZone
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

object Main {
    private fun createInputStream(argument: String) =
            if (Files.exists(Paths.get(argument))) {
                FileInputStream(argument)
            } else {
                URL("https://github.com/evansiroky/timezone-boundary-builder/releases/download/" + argument +
                        "/timezones-with-oceans.geojson.zip").openStream()
            }

    private fun List<LngLatAlt>.convertRing(): List<LatLon> =
            this.map { LatLon(it.latitude.toFloat(), it.longitude.toFloat()) }

    private fun cleanseRegion(timeZone: TimeZone): TimeZone {
        val polygon = com.esri.core.geometry.Polygon()
        for (region in timeZone.regions.flatten()) {
            polygon.startPath(region[0].longitude.toDouble(), region[0].latitude.toDouble())
            region.subList(1, region.size)
                    .forEach { (lat, long) -> polygon.lineTo(long.toDouble(), lat.toDouble()) }
        }
        val cleansedPolygon = OperatorSimplify.local()
                .execute(polygon, SpatialReference.create("WGS84_WKID"), true, null) as com.esri.core.geometry.Polygon
        val rings = (0 until cleansedPolygon.pathCount)
                .map { ringIndex ->
                    com.esri.core.geometry.Polygon()
                            .also { it.addPath(cleansedPolygon, ringIndex, true) }
                }
                .map { ring -> ring.coordinates2D.map { LatLon(it.y.toFloat(), it.x.toFloat()) } }
        return TimeZone(timeZone.timeZoneId, listOf(rings))
    }

    private fun getBoundingBox(timeZone: TimeZone): Envelope {
        val floats = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        timeZone.regions.asSequence()
                .flatten()
                .flatten()
                .forEach { (latitude, longitude) ->
                    floats[0] = min(floats[0], latitude)
                    floats[1] = min(floats[1], longitude)
                    floats[2] = max(floats[2], latitude)
                    floats[3] = max(floats[3], longitude)
                }
        return Envelope(LatLon(floats[0], floats[1]), LatLon(floats[2], floats[3]))
    }

    private fun <T> List<T>.unflatten() = listOf(this)

    private fun convertFeatureToTimeZones(feature: Feature): List<TimeZone> {
        val timeZoneId = feature.properties["tzid"].toString()
        // Sequence == Distinct regions that don't overlap each other in this region
        // List<List<LatLon>> == A distinct region that may have holes. Doesn't overlap any other region in this feature
        // List<LatLon> == A ring inside a region. The first ring is the outer boundary. Any other rings are holes or
        // islands within the holes.
        val regions: List<List<List<LatLon>>> = when (val geometry = feature.geometry) {
            is Polygon -> (geometry.exteriorRing.unflatten() + geometry.interiorRings)
                    .map { it.convertRing() }
                    .unflatten()

            is MultiPolygon -> geometry.coordinates.map { polygon -> polygon.map { it.convertRing() } }
            else -> throw RuntimeException("Geometries of type ${geometry.javaClass} are not supported")
        }

        return regions.map { TimeZone(timeZoneId, listOf(it)) }
    }

    @Throws(IOException::class)
    private fun build(mapDataLocation: String, mapArchiveVersion: String,
            compressionAndOutputPathPairs: List<Pair<(OutputStream) -> OutputStream, Path>>) {
        ZipInputStream(createInputStream(mapDataLocation)).use { zipInputStream ->
            zipInputStream.nextEntry
            val featureCollection =
                    ObjectMapper().readValue(zipInputStream, FeatureCollection::class.java)
            val serializedTimeZones =
                    featureCollection.features.asSequence()
                            .flatMap { convertFeatureToTimeZones(it).asSequence() }
                            .map { cleanseRegion(it) } // Filter all regions that are now empty after cleansing
                            .filter { (_, regions) ->
                                regions.asSequence().flatten().flatten().firstOrNull() != null
                            }
                            .map { t: TimeZone -> Pair(getBoundingBox(t), t) }
                            .map {(envelope, timeZone) ->
                                SerializedTimeZone("${timeZone.timeZoneId}/${serializeEnvelope(envelope)}",
                                        serializeTimeZone(timeZone))
                            }
                            .toMutableList()
            serializedTimeZones.add(0, SerializedTimeZone(mapArchiveVersion, ByteBuffer.allocate(0)))

            for (compressionAndOutputPath in compressionAndOutputPathPairs) {
                writeMapArchive(compressionAndOutputPath.first, compressionAndOutputPath.second, serializedTimeZones)
            }
        }
    }

    data class SerializedTimeZone(val filename: String, val serializedTimeZone: ByteBuffer)

    @Throws(IOException::class)
    private fun writeMapArchive(compressionProvider: (OutputStream) -> OutputStream,
            outputPath: Path, serializedTimeZones: Collection<SerializedTimeZone>) {
        Files.createDirectories(outputPath.parent)
        TarArchiveOutputStream(compressionProvider(FileOutputStream(outputPath.toString()))).use { out ->
            serializedTimeZones.forEach {
                val entry = TarArchiveEntry(it.filename)
                entry.size = it.serializedTimeZone.remaining().toLong()
                out.putArchiveEntry(entry)
                out.write(it.serializedTimeZone.array(), it.serializedTimeZone.position(),
                        it.serializedTimeZone.remaining())
                out.closeArchiveEntry()
            }
        }
    }

    // Format: <inputShapeZip|versionToDownload> <outputMapVersion> <<uncompressed|zstd> <outputPath>>+
    // Example: timezones-with-oceans.geojson.zip 3.1:2018i uncompressed map.tar zstd map.tar.zstd
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val compressionAndOutputPathPairs: MutableList<Pair<(OutputStream) -> OutputStream, Path>> = mutableListOf()

        try {
            var i = 3
            while (i < args.size) {
                val compression: (OutputStream) -> OutputStream =
                        if (args[i - 1] == "zstd") {
                            { ZstdCompressorOutputStream(it, 22) }
                        } else {
                            { it }
                        }
                compressionAndOutputPathPairs.add(Pair(compression, Paths.get(args[i])))
                i += 2
            }
            build(args[0], "Version: " + args[1], compressionAndOutputPathPairs)
        } catch (e: Exception) {
            System.err.println("Error encountered.\n" +
                    "Required format: <inputShapeZip|versionToDownload> <outputMapVersion> " +
                    "<<uncompressed|zstd> <outputPath>>+\n")
            e.printStackTrace(System.err)
        }
    }
}