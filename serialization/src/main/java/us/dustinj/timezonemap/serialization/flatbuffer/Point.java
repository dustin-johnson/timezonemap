// automatically generated by the FlatBuffers compiler, do not modify

package us.dustinj.timezonemap.serialization.flatbuffer;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class Point extends Struct {
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public Point __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public float latitude() { return bb.getFloat(bb_pos + 0); }
  public float longitude() { return bb.getFloat(bb_pos + 4); }

  public static int createPoint(FlatBufferBuilder builder, float latitude, float longitude) {
    builder.prep(4, 8);
    builder.putFloat(longitude);
    builder.putFloat(latitude);
    return builder.offset();
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public Point get(int j) { return get(new Point(), j); }
    public Point get(Point obj, int j) {  return obj.__assign(__element(j), bb); }
  }
}

