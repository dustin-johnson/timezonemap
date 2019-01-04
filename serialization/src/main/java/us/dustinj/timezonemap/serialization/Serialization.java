package us.dustinj.timezonemap.serialization;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.flatbuffers.FlatBufferBuilder;

import us.dustinj.timezonemap.serialization.flatbuffer.AnchorPoint;
import us.dustinj.timezonemap.serialization.flatbuffer.DeltaPoint;
import us.dustinj.timezonemap.serialization.flatbuffer.Polygon;
import us.dustinj.timezonemap.serialization.flatbuffer.Ring;
import us.dustinj.timezonemap.serialization.flatbuffer.TimeZone;

public final class Serialization {
    private static final int MAX_DELTA_INCREMENT = Integer.MAX_VALUE;

    // Utility class
    private Serialization() {}

    private static int serializeRing(FlatBufferBuilder builder, List<LatLon> ring, int incrementsPerDegree) {
        // Reverse the list as flat buffers reverses it during serialization and we'd prefer to keep the original order.
        List<LatLon> reversedRing = new ArrayList<>(ring);
        Collections.reverse(reversedRing);

        List<FixedPrecisionLatLon> fixedPrecisionLocations = reversedRing.stream()
                .map(p -> convertToFixedPrecision(p, incrementsPerDegree))
                .collect(Collectors.toList());

        List<FixedPrecisionLatLon> deltaLocations = new ArrayList<>(fixedPrecisionLocations.size() - 1);
        FixedPrecisionLatLon previousLocation = fixedPrecisionLocations.get(0);
        for (FixedPrecisionLatLon location : fixedPrecisionLocations.subList(1, fixedPrecisionLocations.size())) {
            deltaLocations.add(new FixedPrecisionLatLon(
                    location.latitude - previousLocation.latitude,
                    location.longitude - previousLocation.longitude));
        }

        deltaLocations = deltaLocations.stream()
                .flatMap(l -> {
                    int intermediatePointsNeeded = Math.max(
                            Math.abs(l.latitude / MAX_DELTA_INCREMENT),
                            Math.abs(l.longitude / MAX_DELTA_INCREMENT));

                    return IntStream.range(0, intermediatePointsNeeded + 1)
                            .mapToObj(i -> new FixedPrecisionLatLon(
                                    getIntermediateIncrement(l.latitude, i),
                                    getIntermediateIncrement(l.longitude, i)));
                })
                .collect(Collectors.toList());

        Ring.startSubsequentPointsVector(builder, deltaLocations.size());
        deltaLocations.forEach(point -> DeltaPoint.createDeltaPoint(builder, point.latitude, point.longitude));
        int subsequentPointsVectorOffset = builder.endVector();

        Ring.startRing(builder);
        Ring.addFirstPoint(builder, AnchorPoint.createAnchorPoint(builder,
                fixedPrecisionLocations.get(0).latitude, fixedPrecisionLocations.get(0).longitude));
        Ring.addSubsequentPoints(builder, subsequentPointsVectorOffset);

        return Ring.endRing(builder);
    }

    private static int getIntermediateIncrement(int value, int incrementIndex) {
        return value < 0 ? Math.min(Math.max(-MAX_DELTA_INCREMENT, value + (MAX_DELTA_INCREMENT * incrementIndex)), 0) :
                Math.max(Math.min(MAX_DELTA_INCREMENT, value - (MAX_DELTA_INCREMENT * incrementIndex)), 0);
    }

    private static int serializePolygon(FlatBufferBuilder builder, List<List<LatLon>> polygon,
            int incrementsPerDegree) {
        int[] regionOffsets = polygon.stream()
                .mapToInt(ring -> serializeRing(builder, ring, incrementsPerDegree))
                .toArray();

        int regionsOffset = Polygon.createRingsVector(builder, regionOffsets);
        return Polygon.createPolygon(builder, regionsOffset);
    }

    private static List<List<LatLon>> deserializePolygon(Polygon polygon, int incrementsPerDegree) {
        List<List<LatLon>> convertedRings = new ArrayList<>(polygon.ringsLength());

        for (int ringIndex = 0; ringIndex < polygon.ringsLength(); ringIndex++) {
            Ring ring = polygon.rings(ringIndex);
            List<LatLon> convertedRing = new ArrayList<>(ring.subsequentPointsLength() + 1);

            convertedRing.add(new LatLon(
                    ((double) ring.firstPoint().latitude()) / incrementsPerDegree,
                    ((double) ring.firstPoint().longitude()) / incrementsPerDegree));
            for (int pointIndex = 0; pointIndex < ring.subsequentPointsLength(); pointIndex++) {
                DeltaPoint point = ring.subsequentPoints(pointIndex);
                convertedRing.add(new LatLon(
                        ((double) point.latitude()) / incrementsPerDegree,
                        ((double) point.longitude()) / incrementsPerDegree));
            }

            convertedRings.add(convertedRing);
        }

        return convertedRings;
    }

    public static String serializeEnvelope(Envelope envelope) {
        return String.format("%f,%f,%f,%f",
                envelope.getLowerLeftCorner().getLatitude(), envelope.getLowerLeftCorner().getLongitude(),
                envelope.getUpperRightCorner().getLatitude(), envelope.getUpperRightCorner().getLongitude());
    }

    public static Envelope deserializeEnvelope(String envelope) {
        String[] fragments = envelope.split(",");
        return new Envelope(
                new LatLon(Float.parseFloat(fragments[0]), Float.parseFloat(fragments[1])),
                new LatLon(Float.parseFloat(fragments[2]), Float.parseFloat(fragments[3])));

    }

    public static ByteBuffer serializeTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone,
            int incrementsPerDegree) {
        FlatBufferBuilder builder = new FlatBufferBuilder(
                timeZone.getRegions().stream().mapToInt(List::size).sum() * 8 +
                        timeZone.getTimeZoneId().length() * 2 + 256);

        int[] regionOffsets = timeZone.getRegions().stream()
                .map(region -> serializePolygon(builder, region, incrementsPerDegree))
                .mapToInt(i -> i)
                .toArray();
        int regionsOffset = TimeZone.createRegionsVector(builder, regionOffsets);
        builder.finish(TimeZone.createTimeZone(builder, builder.createString(timeZone.getTimeZoneId()), regionsOffset));

        return builder.dataBuffer();
    }

    public static us.dustinj.timezonemap.serialization.TimeZone deserializeTimeZone(ByteBuffer serializedTimeZone,
            int incrementsPerDegree) {
        TimeZone timeZone = TimeZone.getRootAsTimeZone(serializedTimeZone);
        List<List<List<LatLon>>> regions = new ArrayList<>();

        for (int i = 0; i < timeZone.regionsLength(); i++) {
            regions.add(deserializePolygon(timeZone.regions(i), incrementsPerDegree));
        }

        return new us.dustinj.timezonemap.serialization.TimeZone(timeZone.timeZoneName(), regions);
    }

    private static FixedPrecisionLatLon convertToFixedPrecision(LatLon location, int incrementsPerDegree) {
        return new FixedPrecisionLatLon(
                Math.toIntExact(Math.round(location.getLatitude() * incrementsPerDegree)),
                Math.toIntExact(Math.round(location.getLongitude() * incrementsPerDegree)));
    }

    private static short toShortExact(long value) {
        if ((short) value != value) {
            throw new ArithmeticException("integer overflow");
        }
        return (short) value;
    }

    private static class FixedPrecisionLatLon {
        final int latitude;
        final int longitude;

        FixedPrecisionLatLon(int latitude, int longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
