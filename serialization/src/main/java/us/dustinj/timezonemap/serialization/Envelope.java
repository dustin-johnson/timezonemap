package us.dustinj.timezonemap.serialization;

import java.util.Objects;

public final class Envelope {
    private final LatLon lowerLeftCorner;
    private final LatLon upperRightCorner;

    public Envelope(LatLon lowerLeftCorner, LatLon upperRightCorner) {
        this.lowerLeftCorner = lowerLeftCorner;
        this.upperRightCorner = upperRightCorner;
    }

    public LatLon getLowerLeftCorner() {
        return lowerLeftCorner;
    }

    public LatLon getUpperRightCorner() {
        return upperRightCorner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Envelope envelope = (Envelope) o;
        return Objects.equals(getLowerLeftCorner(), envelope.getLowerLeftCorner()) &&
                Objects.equals(getUpperRightCorner(), envelope.getUpperRightCorner());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLowerLeftCorner(), getUpperRightCorner());
    }
}

