package us.dustinj.timezonemap.serialization;

import static us.dustinj.timezonemap.serialization.flatbuffer.Polygon.createPolygon;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.flatbuffers.FlatBufferBuilder;

import us.dustinj.timezonemap.serialization.flatbuffer.Point;
import us.dustinj.timezonemap.serialization.flatbuffer.Polygon;
import us.dustinj.timezonemap.serialization.flatbuffer.TimeZone;

public final class Serialization {
    // Utility class
    private Serialization() {}

    private static int serializePolygon(FlatBufferBuilder builder, List<LatLon> polygon) {
        // Reverse the list as flat buffers reverses it during serialization and we'd prefer to keep the original
        // order, even if we don't have a specific reason for doing so.
        List<LatLon> reversedList = new ArrayList<>(polygon);
        Collections.reverse(reversedList);

        Polygon.startRegionVector(builder, reversedList.size());
        reversedList.forEach(point -> Point.createPoint(builder, point.latitude, point.longitude));

        return Polygon.createPolygon(builder, builder.endVector());
    }

    private static List<LatLon> deserializePolygon(Polygon polygon) {
        List<LatLon> region = new ArrayList<>(polygon.regionLength());

        for (int i = 0; i < polygon.regionLength(); i++) {
            Point point = polygon.region(i);
            region.add(new LatLon(point.latitude(), point.longitude()));
        }

        return region;
    }

    public static ByteBuffer serialize(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
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

    public static us.dustinj.timezonemap.serialization.TimeZone deserialize(ByteBuffer serializedTimeZone) {
        TimeZone timeZone = TimeZone.getRootAsTimeZone(serializedTimeZone);
        List<List<LatLon>> regions = new ArrayList<>();

        for (int i = 0; i < timeZone.regionsLength(); i++) {
            regions.add(deserializePolygon(timeZone.regions(i)));
        }

        return new us.dustinj.timezonemap.serialization.TimeZone(timeZone.timeZoneName(), regions);
    }
}
