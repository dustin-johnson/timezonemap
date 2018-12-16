// automatically generated, do not modify

package us.dustinj.timezonemap.serialization.flatbuffer;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class Point extends Struct {
  public Point __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public float latitude() { return bb.getFloat(bb_pos + 0); }
  public float longitude() { return bb.getFloat(bb_pos + 4); }

  public static int createPoint(FlatBufferBuilder builder, float latitude, float longitude) {
    builder.prep(4, 8);
    builder.putFloat(longitude);
    builder.putFloat(latitude);
    return builder.offset();
  }
};

