package us.dustinj.timezonemap.builder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.geojson.MultiPolygon;
import org.geojson.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import com.esri.core.geometry.OperatorSimplify;
import com.esri.core.geometry.SpatialReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dustinj.timezonemap.serialization.Envelope;
import us.dustinj.timezonemap.serialization.LatLon;
import us.dustinj.timezonemap.serialization.Serialization;
import us.dustinj.timezonemap.serialization.TimeZone;

public class Main {

    private static class Pair<X, Y> {
        final X first;
        final Y second;

        private Pair(X first, Y second) {
            this.first = first;
            this.second = second;
        }
    }

    private static InputStream createInputStream(String argument) throws IOException {
        if (Files.exists(Paths.get(argument))) {
            return new FileInputStream(argument);
        } else {
            String url = "https://github.com/evansiroky/timezone-boundary-builder/releases/download/" + argument +
                    "/timezones-with-oceans.geojson.zip";
            return new URL(url).openStream();
        }
    }

    private static List<LatLon> convertToList(List<LngLatAlt> from) {
        return from.stream()
                .map(point -> new LatLon((float) point.getLatitude(), (float) point.getLongitude()))
                .collect(Collectors.toList());
    }

    private static TimeZone cleanseRegion(TimeZone timeZone) {
        com.esri.core.geometry.Polygon polygon = new com.esri.core.geometry.Polygon();

        for (List<LatLon> region : timeZone.getRegions().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList())) {
            polygon.startPath((double) region.get(0).getLongitude(), (double) region.get(0).getLatitude());
            region.subList(1, region.size())
                    .forEach(p -> polygon.lineTo(p.getLongitude(), p.getLatitude()));
        }

        com.esri.core.geometry.Polygon cleansedPolygon = (com.esri.core.geometry.Polygon) OperatorSimplify.local()
                .execute(polygon, SpatialReference.create("WGS84_WKID"), true, null);

        List<com.esri.core.geometry.Polygon> rings = new ArrayList<>();
        for (int i = 0; i < cleansedPolygon.getPathCount(); i++) {
            com.esri.core.geometry.Polygon ring = new com.esri.core.geometry.Polygon();

            ring.addPath(cleansedPolygon, i, true);
            rings.add(ring);
        }

        return new TimeZone(timeZone.getTimeZoneId(), Collections.singletonList(rings.stream()
                .map(r -> Arrays.stream(r.getCoordinates2D())
                        .map(p -> new LatLon((float) p.y, (float) p.x))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList())));
    }

    private static Envelope getBoundingBox(TimeZone timeZone) {
        double[] doubles = new double[] { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };

        timeZone.getRegions().stream()
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .forEach(location -> {
                    doubles[0] = Math.min(doubles[0], location.getLatitude());
                    doubles[1] = Math.min(doubles[1], location.getLongitude());
                    doubles[2] = Math.max(doubles[2], location.getLatitude());
                    doubles[3] = Math.max(doubles[3], location.getLongitude());
                });

        return new Envelope(new LatLon(doubles[0], doubles[1]), new LatLon(doubles[2], doubles[3]));
    }

    private static Stream<TimeZone> convertFeatureToTimeZones(Feature feature) {
        String timeZoneId = feature.getProperties().get("tzid").toString();
        // Stream == Distinct regions that don't overlap each other in this region
        // List<List<LatLon>> == A distinct region that may have holes. Doesn't overlap any other region in this feature
        // List<LatLon> == A ring inside a region. The first ring is the outer boundary. Any other rings are holes or
        // islands within the holes.
        Stream<List<List<LatLon>>> regions;

        GeoJsonObject geometry = feature.getGeometry();
        if (geometry instanceof Polygon) {
            regions = Stream.of(Stream
                    .concat(
                            Stream.of(((Polygon) geometry).getExteriorRing()),
                            ((Polygon) geometry).getInteriorRings().stream())
                    .map(Main::convertToList)
                    .collect(Collectors.toList()));
        } else if (geometry instanceof MultiPolygon) {
            regions = ((MultiPolygon) geometry).getCoordinates().stream()
                    .map(polygon -> polygon.stream()
                            .map(Main::convertToList)
                            .collect(Collectors.toList()));
        } else {
            throw new RuntimeException("Geometries of type " + geometry.getClass() + " are not supported");
        }

        return regions.map(r -> new TimeZone(timeZoneId, Collections.singletonList(r)));
        // return Stream.of(new TimeZone("Consolidated_" + timeZoneId, regions.collect(Collectors.toList())));
    }

    private static void build(String argument, String outputPath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(createInputStream(argument))) {
            zipInputStream.getNextEntry();
            FeatureCollection featureCollection = new ObjectMapper().readValue(zipInputStream, FeatureCollection.class);
            Iterator<Pair<String, ByteBuffer>> serializedTimeZones = featureCollection.getFeatures().stream()
                    .flatMap(Main::convertFeatureToTimeZones)
                    .map(Main::cleanseRegion)
                    // Filter all regions that are now empty after cleansing
                    .filter(t -> t.getRegions().stream()
                            .flatMap(Collection::stream)
                            .flatMap(Collection::stream)
                            .findFirst()
                            .isPresent())
                    .peek(t -> System.out.println(t.getTimeZoneId()))
                    .map(t -> new Pair<>(getBoundingBox(t), t))
                    .map(p -> new Pair<>(
                            p.second.getTimeZoneId() + "/" + Serialization.serializeEnvelope(p.first),
                            Serialization.serializeTimeZone(p.second, 1_000_000)))
                    .iterator();

            writeMapArchive(outputPath, serializedTimeZones);
        }
    }

    private static void build2(String argument, String outputPath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(createInputStream(argument));
                TarArchiveOutputStream out = new TarArchiveOutputStream(
                        new ZstdCompressorOutputStream(new FileOutputStream(outputPath), 25))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();

            TarArchiveEntry entry = new TarArchiveEntry(zipEntry.getName());

            entry.setSize(zipEntry.getSize());
            out.putArchiveEntry(entry);

            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[(int) zipEntry.getSize()]);
            int readLength;

            while ((readLength =
                    zipInputStream.read(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining())) != -1 &&
                    byteBuffer.position() < zipEntry.getSize()) {
                byteBuffer.position(byteBuffer.position() + readLength);
            }

            byteBuffer.position(0);

            out.write(byteBuffer.array(), byteBuffer.position(), byteBuffer.remaining());
            out.closeArchiveEntry();
        }
    }

    private static void writeMapArchive(String outputPath, Iterator<Pair<String, ByteBuffer>> serializedTimeZones)
            throws IOException {
        Files.createDirectories(Paths.get(outputPath).getParent());

        try (TarArchiveOutputStream out =
                new TarArchiveOutputStream(new ZstdCompressorOutputStream(new FileOutputStream(outputPath), 25))) {
            while (serializedTimeZones.hasNext()) {
                Pair<String, ByteBuffer> pair = serializedTimeZones.next();
                String filename = pair.first;
                ByteBuffer serializedTimeZone = pair.second;
                TarArchiveEntry entry = new TarArchiveEntry(filename);

                entry.setSize(serializedTimeZone.remaining());
                out.putArchiveEntry(entry);
                out.write(serializedTimeZone.array(), serializedTimeZone.position(), serializedTimeZone.remaining());
                out.closeArchiveEntry();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        build2("C:\\Users\\Dustin\\Downloads\\timezones-with-oceans.geojson.zip",
                "C:\\Users\\Dustin\\gitroot\\timezonemap\\whole.tar");

        if (args.length == 2) {
            build(args[0], args[1]);
        } else {
            build("C:\\Users\\Dustin\\Downloads\\timezones-with-oceans.geojson.zip",
                    "C:\\Users\\Dustin\\gitroot\\timezonemap\\float.tar");
        }
    }
}
