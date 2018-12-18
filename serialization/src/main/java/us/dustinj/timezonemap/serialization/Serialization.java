package us.dustinj.timezonemap.serialization;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.flatbuffers.FlatBufferBuilder;

import us.dustinj.timezonemap.serialization.flatbuffer.Point;
import us.dustinj.timezonemap.serialization.flatbuffer.TimeZone;

public final class Serialization {
    // Utility class
    private Serialization() {}

    public static ByteBuffer serialize(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        FlatBufferBuilder builder =
                new FlatBufferBuilder(timeZone.getRegion().size() * 8 + timeZone.getTimeZoneId().length() * 2 + 256);

        // Reverse the list as flat buffers reverses it during serialization and we'd prefer to keep the original
        // order, even if we don't have a specific reason for doing so.
        List<LatLon> reversedPolygon = new ArrayList<>(timeZone.getRegion());
        Collections.reverse(reversedPolygon);

        TimeZone.startRegionVector(builder, timeZone.getRegion().size());
        reversedPolygon.forEach(point -> Point.createPoint(builder, point.latitude, point.longitude));
        int polygonOffset = builder.endVector();

        builder.finish(TimeZone.createTimeZone(builder, builder.createString(timeZone.getTimeZoneId()), polygonOffset));

        return builder.dataBuffer();
    }

    public static us.dustinj.timezonemap.serialization.TimeZone deserialize(ByteBuffer serializedTimeZone) {
        TimeZone timeZone = TimeZone.getRootAsTimeZone(serializedTimeZone);
        List<LatLon> region = new ArrayList<>(timeZone.regionLength());

        for (int i = 0; i < timeZone.regionLength(); i++) {
            Point point = timeZone.region(i);
            region.add(new LatLon(point.latitude(), point.longitude()));
        }

        return new us.dustinj.timezonemap.serialization.TimeZone(timeZone.timeZoneName(), region);
    }
}
