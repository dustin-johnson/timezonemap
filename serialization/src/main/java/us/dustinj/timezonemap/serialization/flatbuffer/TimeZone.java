// automatically generated, do not modify

package us.dustinj.timezonemap.serialization.flatbuffer;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class TimeZone extends Table {
  public static TimeZone getRootAsTimeZone(ByteBuffer _bb) { return getRootAsTimeZone(_bb, new TimeZone()); }
  public static TimeZone getRootAsTimeZone(ByteBuffer _bb, TimeZone obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public TimeZone __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public String timeZoneName() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer timeZoneNameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public Point region(int j) { return region(new Point(), j); }
  public Point region(Point obj, int j) { int o = __offset(6); return o != 0 ? obj.__init(__vector(o) + j * 8, bb) : null; }
  public int regionLength() { int o = __offset(6); return o != 0 ? __vector_len(o) : 0; }

  public static int createTimeZone(FlatBufferBuilder builder,
      int timeZoneName,
      int region) {
    builder.startObject(2);
    TimeZone.addRegion(builder, region);
    TimeZone.addTimeZoneName(builder, timeZoneName);
    return TimeZone.endTimeZone(builder);
  }

  public static void startTimeZone(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addTimeZoneName(FlatBufferBuilder builder, int timeZoneNameOffset) { builder.addOffset(0, timeZoneNameOffset, 0); }
  public static void addRegion(FlatBufferBuilder builder, int regionOffset) { builder.addOffset(1, regionOffset, 0); }
  public static void startRegionVector(FlatBufferBuilder builder, int numElems) { builder.startVector(8, numElems, 4); }
  public static int endTimeZone(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishTimeZoneBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

