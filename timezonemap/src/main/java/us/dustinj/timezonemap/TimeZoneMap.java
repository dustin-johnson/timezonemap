package us.dustinj.timezonemap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryCursor;
import com.esri.core.geometry.OperatorIntersection;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.SimpleGeometryCursor;

import us.dustinj.timezonemap.data.DataLocator;
import us.dustinj.timezonemap.serialization.Envelope;
import us.dustinj.timezonemap.serialization.Serialization;
import us.dustinj.timezonemap.utils.Preconditions;

@SuppressWarnings("WeakerAccess")
public final class TimeZoneMap {
    private final String mapVersion;
    private final List<TimeZone> timeZones;
    private final Envelope2D initializedRegion;

    private TimeZoneMap(String mapVersion, List<TimeZone> timeZones, Envelope2D initializedRegion) {
        this.mapVersion = mapVersion;
        this.timeZones = timeZones;
        this.initializedRegion = initializedRegion;
    }

    /**
     * Creates a new instance of {@link TimeZoneMap} and initializes it to be valid for the entire world. This is a
     * blocking long running operation that takes about 1-2 seconds on desktop hardware. If performance is a concern,
     * consider using {@link #forRegion(double, double, double, double)}.
     *
     * @return A map instance that can be used for querying locations anywhere in the world.
     */
    public static TimeZoneMap forEverywhere() {
        return forRegion(-90.0, -180.0, 90.0, 180.0);
    }

    /**
     * Creates a new instance of {@link TimeZoneMap} using the default map data and initializes it to be valid for
     * anywhere within the provided coordinates (inclusive). This is a blocking long-running operation that can be very
     * quick depending on the area described by the provided coordinates. Initializing the map this way not only
     * improves speed, but also reduces memory usage, as only the regions withing the provided coordinates are held in
     * memory. Large time zone regions are clipped so that only the pieces within the provided coordinates are kept in
     * memory.
     *
     * @param minDegreesLatitude
     *         Between -90.0 and 90.0, which are the South and North pole respectively. This value is the southern most
     *         boundary to be indexed, inclusive.
     * @param minDegreesLongitude
     *         Between -180.0 and 180.0, which are farthest West and East respectively. The White House of the United
     *         States is at -77.036586 degrees longitude. This value is the western most boundary to be indexed,
     *         inclusive.
     * @param maxDegreesLatitude
     *         Between -90.0 and 90.0, which are the South and North pole respectively. This value is the northern most
     *         boundary to be indexed, inclusive.
     * @param maxDegreesLongitude
     *         Between -180.0 and 180.0, which are farthest West and East respectively. The White House of the United
     *         States is at -77.036586 degrees longitude. This value is the eastern most boundary to be indexed,
     *         inclusive.
     * @return A map instance that can be used for querying locations withing the provided coordinates, inclusive.
     * @throws IllegalArgumentException
     *         If minimum values aren't less than maximum values.
     * @see #forRegion(InputStream, double, double, double, double)
     */
    public static TimeZoneMap forRegion(double minDegreesLatitude, double minDegreesLongitude,
            double maxDegreesLatitude, double maxDegreesLongitude) {
        try (InputStream inputStream = DataLocator.getDataInputStream()) {
            return forRegion(inputStream, minDegreesLatitude, minDegreesLongitude, maxDegreesLatitude,
                    maxDegreesLongitude);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read time zone data resource file", e);
        }
    }

    /**
     * Creates a new instance of {@link TimeZoneMap} and initializes it to be valid for anywhere within the provided
     * coordinates (inclusive). This is a blocking long-running operation that can be very quick depending on the area
     * described by the provided coordinates. Initializing the map this way not only improves speed, but also reduces
     * memory usage, as only the regions withing the provided coordinates are held in memory. Large time zone regions
     * are clipped so that only the pieces within the provided coordinates are kept in memory.
     *
     * @param tarInputStream
     *         A stream containing the tar archive. Any compression or other packaging must have already been unwrapped
     *         before this.
     * @param minDegreesLatitude
     *         Between -90.0 and 90.0, which are the South and North pole respectively. This value is the southern most
     *         boundary to be indexed, inclusive.
     * @param minDegreesLongitude
     *         Between -180.0 and 180.0, which are farthest West and East respectively. The White House of the United
     *         States is at -77.036586 degrees longitude. This value is the western most boundary to be indexed,
     *         inclusive.
     * @param maxDegreesLatitude
     *         Between -90.0 and 90.0, which are the South and North pole respectively. This value is the northern most
     *         boundary to be indexed, inclusive.
     * @param maxDegreesLongitude
     *         Between -180.0 and 180.0, which are farthest West and East respectively. The White House of the United
     *         States is at -77.036586 degrees longitude. This value is the eastern most boundary to be indexed,
     *         inclusive.
     * @return A map instance that can be used for querying locations withing the provided coordinates, inclusive.
     * @throws IllegalArgumentException
     *         If minimum values aren't less than maximum values.
     * @see #forRegion(double, double, double, double)
     */
    public static TimeZoneMap forRegion(InputStream tarInputStream, double minDegreesLatitude,
            double minDegreesLongitude, double maxDegreesLatitude, double maxDegreesLongitude) {
        Preconditions.checkArgument(minDegreesLatitude < maxDegreesLatitude,
                "Minimum latitude must be less than maximum latitude");
        Preconditions.checkArgument(minDegreesLongitude < maxDegreesLongitude,
                "Minimum longitude must be less than maximum longitude");

        Envelope2D indexAreaEnvelope = new Envelope2D(minDegreesLongitude, minDegreesLatitude,
                maxDegreesLongitude, maxDegreesLatitude);
        Polygon indexAreaPolygon = envelopeToPolygon(indexAreaEnvelope);

        try (TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(tarInputStream)) {
            AtomicReference<String> mapVersion = new AtomicReference<>(); // Atomic due to Java's lambda limitations
            List<TimeZone> timeZones = getTarEntryStream(archiveInputStream)
                    .peek(entry -> {
                        if (mapVersion.get() == null) {
                            String[] splitVersion = entry.getName().split(" ");
                            String version = splitVersion.length == 2 ? splitVersion[1] : entry.getName();

                            if (!version.split(":")[0].equals(getVersion())) {
                                throw new IllegalArgumentException("Incompatible map archive. Detected version is '" +
                                        version + "' required version '" + getVersion() + ":*'");
                            }

                            mapVersion.compareAndSet(null, version);
                        }
                    })
                    .filter(entry -> entry.getSize() > 0)
                    // The name of each file is an envelope that is the outside boundary of the time zone. This
                    // allows us to immediately filter out any time zones that don't overlap the initialization
                    // region without having to deserialize the region, which is a fairly expensive operation.
                    .filter(entry -> {
                        String[] fragmentedName = entry.getName().split("/");
                        String serializedEnvelope = fragmentedName[fragmentedName.length - 1];
                        Envelope envelope = Serialization.deserializeEnvelope(serializedEnvelope);

                        return indexAreaEnvelope.isIntersecting(
                                envelope.getLowerLeftCorner().getLongitude(),
                                envelope.getLowerLeftCorner().getLatitude(),
                                envelope.getUpperRightCorner().getLongitude(),
                                envelope.getUpperRightCorner().getLatitude());
                    })
                    .map(n -> {
                        try {
                            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[(int) n.getSize()]);
                            int readLength;

                            while ((readLength = archiveInputStream.read(byteBuffer.array(),
                                    byteBuffer.position(), byteBuffer.remaining())) != -1) {
                                byteBuffer.position(byteBuffer.position() + readLength);
                            }

                            byteBuffer.position(0);

                            return byteBuffer;
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to load time zone file " + n.getName(), e);
                        }
                    })
                    .map(Serialization::deserializeTimeZone)
                    .map(Util::convertToEsriBackedTimeZone)
                    .map(timeZone -> {
                        Envelope2D extents = new Envelope2D();
                        timeZone.getRegion().queryEnvelope2D(extents);

                        return new ExtentsAndTimeZone(extents, timeZone);
                    })
                    // Throw out anything that doesn't at least partially overlap with the index area.
                    .filter(t -> indexAreaEnvelope.isIntersecting(t.extents))
                    // Sort smallest area first so we have a deterministic ordering of there is an overlap.
                    .sorted(Comparator.comparingDouble(t -> t.timeZone.getRegion().calculateArea2D()))
                    // Clip the shape to our indexArea so we don't have to keep large time zones that may
                    // only slightly intersect with the region we're indexing.
                    .flatMap(t -> {
                        if (indexAreaEnvelope.contains(t.extents)) {
                            return Stream.of(t.timeZone);
                        }

                        GeometryCursor intersectedGeometries = OperatorIntersection.local().execute(
                                new SimpleGeometryCursor(t.timeZone.getRegion()),
                                new SimpleGeometryCursor(indexAreaPolygon),
                                Util.SPATIAL_REFERENCE, null, -1);

                        List<Polygon> list = new ArrayList<>();
                        Geometry geometry;
                        while ((geometry = intersectedGeometries.next()) != null) {
                            // Since we're intersecting polygons, the only thing we can get back must be 2 dimensional,
                            // so it's safe to cast everything we get back as a polygon.
                            list.add((Polygon) geometry);
                        }

                        return list.stream()
                                .filter(g -> g.getPointCount() > 0)
                                .map(g -> new TimeZone(t.timeZone.getZoneId(), g));
                    })
                    .collect(Collectors.toList());

            return new TimeZoneMap(mapVersion.get(), timeZones, indexAreaEnvelope);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read time zone data resource file", e);
        }
    }

    /**
     * Get the region (inclusive of the boundary) for which this map was initialized. Only locations with in this region
     * may be queried using this map instance.
     *
     * @return The region (inclusive of the boundary) for which this map was initialized. Only locations with in this
     *         region may be queried using this map instance.
     */
    public Envelope2D getInitializedRegion() {
        return this.initializedRegion;
    }

    /**
     * Get the version of the map. The version consists of two parts separated by a colon. The first part is the
     * version of this map library, and the second part is the version of the map shapes. Example: 3.1:2018i
     *
     * @return The version of the map.
     */
    public String getMapVersion() {
        return this.mapVersion;
    }

    /**
     * A list of all time zone identifiers, and their regions, contained in this index. The list is sorted by the area
     * the time zone covers, smallest first. Note, the area computation used for sorting considers the real-world area
     * the time zone covers regardless of the region the index was initialized for. If this index was initialized using
     * {@link #forRegion(double, double, double, double)}, then the returned list represents all time zones that
     * overlap with the coordinates with which this index was initialized and the regions are clipped to the
     * initialization coordinates.
     * <p>
     * This list represents the full range of time zones that can be returned by {@link #getOverlappingTimeZone(double,
     * double)} or {@link #getOverlappingTimeZones(double, double)}.
     *
     * @return A sort list of known time zones contained in this index.
     */
    public List<TimeZone> getTimeZones() {
        return Collections.unmodifiableList(this.timeZones);
    }

    /**
     * Retrieve the time zone in use at the provided coordinates. The identifier contained in this time zone can be
     * used, in modern Java versions, to initialize the {@code java.util.TimeZone} object and interact with the time
     * zone programmatically.
     *
     * @param degreesLatitude
     *         90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude
     *         180.0 to -180.0 such that positive is East, negative is West, and the White House of the United States
     *         is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     * @return A time zone that is in use at the provided coordinates, if such a time zone exists. If multiple time
     *         zones overlap the provided coordinates, as can happen in disputed areas such as the South China Sea, then
     *         the time zone with the smallest land area will be provided. If this index was initialized using {@link
     *         #forRegion(double, double, double, double)}, then the returned time zone region is clipped to the
     *         initialization coordinates.
     * @throws IllegalArgumentException
     *         If the provided coordinates are outside of the area indexed by this instance of the time zone index.
     */
    public Optional<TimeZone> getOverlappingTimeZone(double degreesLatitude, double degreesLongitude) {
        return getOverlappingTimeZoneStream(degreesLatitude, degreesLongitude)
                .findFirst();
    }

    /**
     * Retrieve all time zones in use at the provided coordinates. Multiple time zones can overlap the
     * provided location in disputed areas such as the South China Sea). The returned time zones are sorted such that
     * the first entry in the list is the time zone with the smallest land area. The identifiers contained in the
     * returned time zones can be used in modern Java versions to initialize the {@code java.util.TimeZone} object
     * and interact with the time zone programmatically.
     *
     * @param degreesLatitude
     *         90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude
     *         180.0 to -180.0 such that positive is East, negative is West, and the White House of the United States
     *         is at -77.036586 degrees longitude (and 38.897670 degrees latitude).
     * @return List of time zones that are in use at the provided coordinates, if such time zones exists. If multiple
     *         time zones overlap the provided coordinates, as can happen in disputed areas such as the South China
     *         Sea, then all overlapping time zones are returned, sorted such that the zone with the smallest land area
     *         is first in the list. If no time zones overlap the provided coordinates, then an empty list will be
     *         returned. If this index was initialized using {@link #forRegion(double, double, double, double)}, then
     *         the regions are clipped to the initialization coordinates.
     * @throws IllegalArgumentException
     *         If the provided coordinates are outside of the area indexed by this instance of the time zone index.
     */
    public List<TimeZone> getOverlappingTimeZones(double degreesLatitude, double degreesLongitude) {
        return getOverlappingTimeZoneStream(degreesLatitude, degreesLongitude)
                .collect(Collectors.toList());
    }

    private Stream<TimeZone> getOverlappingTimeZoneStream(double degreesLatitude, double degreesLongitude) {
        Point point = new Point(degreesLongitude, degreesLatitude);

        Preconditions.checkArgument(this.initializedRegion.contains(point.getXY()),
                "Requested point is outside the initialized area");

        return this.timeZones.stream()
                .filter(t -> Util.containsInclusive(t.getRegion(), point));
    }

    static Polygon envelopeToPolygon(Envelope2D envelope) {
        Polygon polygon = new Polygon();

        polygon.startPath(envelope.xmin, envelope.ymax); // Upper left
        polygon.lineTo(envelope.xmax, envelope.ymax); // Upper right
        polygon.lineTo(envelope.xmax, envelope.ymin); // Lower right
        polygon.lineTo(envelope.xmin, envelope.ymin); // Lower left
        polygon.lineTo(envelope.xmin, envelope.ymax); // Upper left

        return polygon;
    }

    private static Stream<TarArchiveEntry> getTarEntryStream(TarArchiveInputStream f) {
        Spliterator<TarArchiveEntry> spliterator =
                new Spliterators.AbstractSpliterator<TarArchiveEntry>(Long.MAX_VALUE, 0) {
                    @Override
                    public boolean tryAdvance(Consumer<? super TarArchiveEntry> action) {
                        try {
                            TarArchiveEntry entry = f.getNextTarEntry();
                            if (entry != null) {
                                action.accept(entry);
                                return true;
                            } else {
                                return false;
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to read time zone data resource file", e);
                        }
                    }
                };

        return StreamSupport.stream(spliterator, false);
    }

    private static String getVersion() {
        return BuildInformation.VERSION;
    }

    private static class ExtentsAndTimeZone {
        final Envelope2D extents;
        final TimeZone timeZone;

        ExtentsAndTimeZone(Envelope2D extents, TimeZone timeZone) {
            this.extents = extents;
            this.timeZone = timeZone;
        }
    }
}
