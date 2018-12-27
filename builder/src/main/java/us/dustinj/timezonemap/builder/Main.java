package us.dustinj.timezonemap.builder;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dustinj.timezonemap.serialization.Envelope;
import us.dustinj.timezonemap.serialization.LatLon;
import us.dustinj.timezonemap.serialization.Serialization;
import us.dustinj.timezonemap.serialization.TimeZone;

public class Main {

    private static class Pair<X, Y> {
        final X x;
        final Y y;

        private Pair(X x, Y y) {
            this.x = x;
            this.y = y;
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

    private static Envelope getBoundingBox(TimeZone timeZone) {
        float[] floats = new float[] { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };

        timeZone.getRegions().stream()
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .forEach(location -> {
                    floats[0] = Math.min(floats[0], location.getLatitude());
                    floats[1] = Math.min(floats[1], location.getLongitude());
                    floats[2] = Math.max(floats[2], location.getLatitude());
                    floats[3] = Math.max(floats[3], location.getLongitude());
                });

        return new Envelope(new LatLon(floats[0], floats[1]), new LatLon(floats[2], floats[3]));
    }

    private static TimeZone convertFeatureToTimeZone(Feature feature) {
        String timeZoneId = feature.getProperties().get("tzid").toString();
        List<List<List<LatLon>>> regions;

        GeoJsonObject geometry = feature.getGeometry();
        if (geometry instanceof Polygon) {
            regions = Collections.singletonList(
                    Collections.singletonList(convertToList(((Polygon) geometry).getExteriorRing())));
        } else if (geometry instanceof MultiPolygon) {
            regions = ((MultiPolygon) geometry).getCoordinates().stream()
                    .map(polygon -> polygon.stream()
                            .map(Main::convertToList)
                            .collect(Collectors.toList()))
                    .collect(Collectors.toList());
        } else {
            throw new RuntimeException("Geometries of type " + geometry.getClass() + " are not supported");
        }

        return new TimeZone(timeZoneId, regions);
    }

    private static void build(String argument, String outputPath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(createInputStream(argument))) {
            zipInputStream.getNextEntry();
            FeatureCollection featureCollection = new ObjectMapper().readValue(zipInputStream, FeatureCollection.class);
            Iterator<Pair<String, ByteBuffer>> serializedTimeZones = featureCollection.getFeatures().stream()
                    .map(Main::convertFeatureToTimeZone)
                    .map(t -> new Pair<>(getBoundingBox(t), t))
                    .map(p -> new Pair<>(Serialization.serializeEnvelope(p.x), Serialization.serializeTimeZone(p.y)))
                    .iterator();

            writeZTar(outputPath, serializedTimeZones);
        }
    }

    private static void writeZTar(String outputPath, Iterator<Pair<String, ByteBuffer>> serializedTimeZones)
            throws IOException {
        Files.createDirectories(Paths.get(outputPath).getParent());

        try (TarArchiveOutputStream out =
                new TarArchiveOutputStream(new ZstdCompressorOutputStream(new FileOutputStream(outputPath), 25))) {
            while (serializedTimeZones.hasNext()) {
                Pair<String, ByteBuffer> pair = serializedTimeZones.next();
                String filename = pair.x;
                ByteBuffer serializedTimeZone = pair.y;
                TarArchiveEntry entry = new TarArchiveEntry(filename);

                entry.setSize(serializedTimeZone.remaining());
                out.putArchiveEntry(entry);
                out.write(serializedTimeZone.array(), serializedTimeZone.position(), serializedTimeZone.remaining());
                out.closeArchiveEntry();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        build(args[0], args[1]);

        // build("C:\\Users\\Dustin\\Downloads\\timezones-with-oceans.geojson.zip",
        //        "C:\\Users\\Dustin\\gitroot\\timezonemap\\float.tar");
    }
}
