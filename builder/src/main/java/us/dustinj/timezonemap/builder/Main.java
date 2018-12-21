package us.dustinj.timezonemap.builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
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

import us.dustinj.timezonemap.serialization.LatLon;
import us.dustinj.timezonemap.serialization.Serialization;
import us.dustinj.timezonemap.serialization.TimeZone;

public class Main {

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

    private static ByteBuffer convertFeature(Feature feature) {
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

        return Serialization.serialize(new TimeZone(timeZoneId, regions));
    }

    private static void build(String argument, String outputPath) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(createInputStream(argument))) {
            zipInputStream.getNextEntry();
            FeatureCollection featureCollection = new ObjectMapper().readValue(zipInputStream, FeatureCollection.class);
            Iterator<ByteBuffer> serializedTimeZones = featureCollection.getFeatures().stream()
                    .map(Main::convertFeature)
                    .iterator();

            writeZTar(outputPath, serializedTimeZones);
        }
    }

    private static void writeZTar(String outputPath, Iterator<ByteBuffer> serializedTimeZones) throws IOException {
        Files.createDirectories(Paths.get(outputPath).getParent());

        try (TarArchiveOutputStream out =
                new TarArchiveOutputStream(new ZstdCompressorOutputStream(new FileOutputStream(outputPath), 25))) {
            int count = 0;
            while (serializedTimeZones.hasNext()) {
                ByteBuffer serializedTimeZone = serializedTimeZones.next();
                TarArchiveEntry entry = new TarArchiveEntry(Integer.toString(count++));

                entry.setSize(serializedTimeZone.remaining());
                out.putArchiveEntry(entry);
                out.write(serializedTimeZone.array(), serializedTimeZone.position(),
                        serializedTimeZone.remaining());
                out.closeArchiveEntry();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        build(args[0], args[1]);

        // build("C:\\Users\\Dustin\\gitroot\\timezonemap\\timezones-with-oceans.geojson.zip",
        //         "C:\\Users\\Dustin\\gitroot\\timezonemap\\float.tar");
    }
}
