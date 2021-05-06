// automatically generated by the FlatBuffers compiler, do not modify

package us.dustinj.timezonemap.serialization.flatbuffer;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class TimeZone extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_1_12_0(); }
  public static TimeZone getRootAsTimeZone(ByteBuffer _bb) { return getRootAsTimeZone(_bb, new TimeZone()); }
  public static TimeZone getRootAsTimeZone(ByteBuffer _bb, TimeZone obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public TimeZone __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String timeZoneName() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer timeZoneNameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer timeZoneNameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public us.dustinj.timezonemap.serialization.flatbuffer.Polygon regions(int j) { return regions(new us.dustinj.timezonemap.serialization.flatbuffer.Polygon(), j); }
  public us.dustinj.timezonemap.serialization.flatbuffer.Polygon regions(us.dustinj.timezonemap.serialization.flatbuffer.Polygon obj, int j) { int o = __offset(6); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int regionsLength() { int o = __offset(6); return o != 0 ? __vector_len(o) : 0; }
  public us.dustinj.timezonemap.serialization.flatbuffer.Polygon.Vector regionsVector() { return regionsVector(new us.dustinj.timezonemap.serialization.flatbuffer.Polygon.Vector()); }
  public us.dustinj.timezonemap.serialization.flatbuffer.Polygon.Vector regionsVector(us.dustinj.timezonemap.serialization.flatbuffer.Polygon.Vector obj) { int o = __offset(6); return o != 0 ? obj.__assign(__vector(o), 4, bb) : null; }

  public static int createTimeZone(FlatBufferBuilder builder,
      int timeZoneNameOffset,
      int regionsOffset) {
    builder.startTable(2);
    TimeZone.addRegions(builder, regionsOffset);
    TimeZone.addTimeZoneName(builder, timeZoneNameOffset);
    return TimeZone.endTimeZone(builder);
  }

  public static void startTimeZone(FlatBufferBuilder builder) { builder.startTable(2); }
  public static void addTimeZoneName(FlatBufferBuilder builder, int timeZoneNameOffset) { builder.addOffset(0, timeZoneNameOffset, 0); }
  public static void addRegions(FlatBufferBuilder builder, int regionsOffset) { builder.addOffset(1, regionsOffset, 0); }
  public static int createRegionsVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startRegionsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endTimeZone(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }
  public static void finishTimeZoneBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
  public static void finishSizePrefixedTimeZoneBuffer(FlatBufferBuilder builder, int offset) { builder.finishSizePrefixed(offset); }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public TimeZone get(int j) { return get(new TimeZone(), j); }
    public TimeZone get(TimeZone obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}

