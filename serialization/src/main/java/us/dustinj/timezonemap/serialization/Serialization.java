package us.dustinj.timezonemap.serialization;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.flatbuffers.FlatBufferBuilder;

import us.dustinj.timezonemap.serialization.flatbuffer.Point;
import us.dustinj.timezonemap.serialization.flatbuffer.Polygon;
import us.dustinj.timezonemap.serialization.flatbuffer.Ring;
import us.dustinj.timezonemap.serialization.flatbuffer.TimeZone;

public final class Serialization {
    // Utility class
    private Serialization() {}

    private static int serializeRing(FlatBufferBuilder builder, List<LatLon> ring) {
        // Reverse the list as flat buffers reverses it during serialization and we'd prefer to keep the original
        // order.
        List<LatLon> reversedList = new ArrayList<>(ring);
        Collections.reverse(reversedList);

        Ring.startPointsVector(builder, reversedList.size());
        reversedList.forEach(point -> Point.createPoint(builder, point.latitude, point.longitude));

        return Ring.createRing(builder, builder.endVector());
    }

    private static int serializePolygon(FlatBufferBuilder builder, List<List<LatLon>> polygon) {
        int[] regionOffsets = polygon.stream()
                .mapToInt(ring -> serializeRing(builder, ring))
                .toArray();

        int regionsOffset = Polygon.createRingsVector(builder, regionOffsets);
        return Polygon.createPolygon(builder, regionsOffset);
    }

    private static List<List<LatLon>> deserializePolygon(Polygon polygon) {
        List<List<LatLon>> convertedRings = new ArrayList<>(polygon.ringsLength());

        for (int ringIndex = 0; ringIndex < polygon.ringsLength(); ringIndex++) {
            Ring ring = polygon.rings(ringIndex);
            List<LatLon> convertedRing = new ArrayList<>(ring.pointsLength());

            for (int pointIndex = 0; pointIndex < ring.pointsLength(); pointIndex++) {
                Point point = ring.points(pointIndex);
                convertedRing.add(new LatLon(point.latitude(), point.longitude()));
            }

            convertedRings.add(convertedRing);
        }

        return convertedRings;
    }

    public static String serializeEnvelope(Envelope envelope) {
        return String.format("%f,%f,%f,%f",
                envelope.getLowerLeftCorner().latitude, envelope.getLowerLeftCorner().longitude,
                envelope.getUpperRightCorner().latitude, envelope.getUpperRightCorner().longitude);
    }

    public static Envelope deserializeEnvelope(String envelope) {
        String[] fragments = envelope.split(",");
        return new Envelope(
                new LatLon(Float.parseFloat(fragments[0]), Float.parseFloat(fragments[1])),
                new LatLon(Float.parseFloat(fragments[2]), Float.parseFloat(fragments[3])));

    }

    public static ByteBuffer serializeTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        FlatBufferBuilder builder = new FlatBufferBuilder(
                timeZone.getRegions().stream().mapToInt(List::size).sum() * 8 +
                        timeZone.getTimeZoneId().length() * 2 + 256);

        int[] regionOffsets = timeZone.getRegions().stream()
                .map(region -> serializePolygon(builder, region))
                .mapToInt(i -> i)
                .toArray();
        int regionsOffset = TimeZone.createRegionsVector(builder, regionOffsets);
        builder.finish(TimeZone.createTimeZone(builder, builder.createString(timeZone.getTimeZoneId()), regionsOffset));

        return builder.dataBuffer();
    }

    public static us.dustinj.timezonemap.serialization.TimeZone deserializeTimeZone(ByteBuffer serializedTimeZone) {
        TimeZone timeZone = TimeZone.getRootAsTimeZone(serializedTimeZone);
        List<List<List<LatLon>>> regions = new ArrayList<>();

        for (int i = 0; i < timeZone.regionsLength(); i++) {
            regions.add(deserializePolygon(timeZone.regions(i)));
        }

        return new us.dustinj.timezonemap.serialization.TimeZone(timeZone.timeZoneName(), regions);
    }
}
