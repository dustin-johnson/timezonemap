package us.dustinj.timezonemap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryCursor;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.OperatorIntersection;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.SimpleGeometryCursor;
import com.esri.core.geometry.SpatialReference;

import us.dustinj.timezonemap.data.DataLocator;
import us.dustinj.timezonemap.serialization.LatLon;
import us.dustinj.timezonemap.serialization.Serialization;

@SuppressWarnings("WeakerAccess")
public final class TimeZoneIndex {
    static final SpatialReference SPATIAL_REFERENCE = SpatialReference.create(4326); // WGS84_WKID = 4326
    private static final Logger LOG = LoggerFactory.getLogger(TimeZoneIndex.class);

    private final List<TimeZone> timeZones;
    private final Envelope2D indexedArea;

    private TimeZoneIndex(List<TimeZone> timeZones, Envelope2D indexedArea) {
        LOG.info("Initialized index with {} time zones described with {} points",
                timeZones.size(),
                timeZones.stream()
                        .map(TimeZone::getRegion)
                        .mapToLong(Polygon::getPointCount)
                        .sum());

        this.timeZones = timeZones;
        this.indexedArea = indexedArea;
    }

    /**
     * Creates a new instance of {@link TimeZoneIndex} and initializes it with an index for the entire world.
     * This is a blocking long running operation that takes about 1-2 seconds on desktop hardware. If performance is
     * a concern, consider using {@link #forRegion(double, double, double, double)}.
     *
     * @return An index that can be used for querying locations anywhere in the world.
     */
    public static TimeZoneIndex forEverywhere() {
        return forRegion(-90.0, -180.0, 90.0, 180.0);
    }

    /**
     * Creates a new instance of {@link TimeZoneIndex} and initializes it with an index valid for anywhere within the
     * provided coordinates (inclusive). This is a blocking long running operation that can be very quick depending on
     * the area described by the provided coordinates. Initializing the index this way not only improves speed, but
     * also reduces memory usage, as only the regions withing the provided coordinates are held in memory, with large
     * regions clipped so that only the pieces within the provided coordinates are kept in memory.
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
     * @return An index that can be used for querying locations withing the provided coordinates, inclusive.
     * @throws IllegalArgumentException
     *         If minimum values aren't less than maximum values.
     */
    public static TimeZoneIndex forRegion(double minDegreesLatitude, double minDegreesLongitude,
            double maxDegreesLatitude, double maxDegreesLongitude) {
        precondition(minDegreesLatitude < maxDegreesLatitude, "Minimum latitude must be less than maximum latitude");
        precondition(minDegreesLongitude < maxDegreesLongitude,
                "Minimum longitude must be less than maximum longitude");

        try (InputStream inputStream = DataLocator.getDataInputStream();
                TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(
                        new ZstdCompressorInputStream(inputStream))) {

            Stream<TimeZone> timeZones =
                    StreamSupport.stream(makeSpliterator(archiveInputStream), false)
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
                                    LOG.error("Unable to read time zone data resource file", e);
                                    throw new RuntimeException(e);
                                }
                            })
                            .map(Serialization::deserialize)
                            .map(TimeZoneIndex::convertToEsriBackedTimeZone);
            Envelope2D indexArea = new Envelope2D(
                    Math.nextDown(minDegreesLongitude),
                    Math.nextDown(minDegreesLatitude),
                    Math.nextUp(maxDegreesLongitude),
                    Math.nextUp(maxDegreesLatitude));

            return new TimeZoneIndex(build(timeZones, indexArea), indexArea);
        } catch (NullPointerException | IOException e) {
            LOG.error("Unable to read time zone data resource file", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * An list of all time zone identifiers contained in this index. The list is sorted by the area the time zone
     * covers, smallest first. Note, the area computation used for sorting considers the real-world area the time
     * zone covers regardless of the region the index was initialized for. If this index was initialized using {@link
     * #forRegion(double, double, double, double)}, then the returned list represents all time zones that overlap with
     * the coordinates with which this index was initialized.
     * <p>
     * This list represents the full range of identifiers that can be returned by {@link #getTimeZone(double, double)}
     * or {@link #getAllTimeZones(double, double)}.
     *
     * @return A sorted list of known time zone identifiers contained in this index.
     */
    public List<String> getKnownZoneIds() {
        return timeZones.stream()
                .map(TimeZone::getZoneId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * An list of all time zone identifiers, and their regions, contained in this index. The list is sorted by the area
     * the time zone covers, smallest first. Note, the area computation used for sorting considers the real-world area
     * the time zone covers regardless of the region the index was initialized for. If this index was initialized using
     * {@link #forRegion(double, double, double, double)}, then the returned list represents all time zones that
     * overlap with the coordinates with which this index was initialized and the regions are clipped to the
     * initialization coordinates.
     * <p>
     * This list represents the full range of time zones that can be returned by {@link #getTimeZone(double, double)}
     * or {@link #getAllTimeZones(double, double)}.
     *
     * @return A sort list of known time zones contained in this index.
     */
    public List<TimeZone> getKnownTimeZones() {
        return Collections.unmodifiableList(this.timeZones);
    }

    /**
     * Retrieve the time zone identifier in use at the provided coordinates. This identifier can be used in modern
     * Java versions to initialize the java.util.TimeZone object and interact with the time zone programmatically.
     *
     * @param degreesLatitude
     *         90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude
     *         180.0 to -180.0 such that the White House of the United States is at -77.036586 degrees longitude
     *         (and 38.897670 degrees latitude).
     * @return The time zone identifier that is in use at the provided coordinates, if such a time zone exists. If
     *         multiple time zones overlap the provided coordinates, as can happen in disputed areas such as the South
     *         China Sea, then the time zone with the smallest land area will be provided.
     * @throws IllegalArgumentException
     *         If the provided coordinates are outside of the area index by this instance of the time zone index.
     */
    public Optional<String> getTimeZone(double degreesLatitude, double degreesLongitude) {
        return getOverlappingTimeZones(degreesLatitude, degreesLongitude)
                .map(TimeZone::getZoneId)
                .findFirst();
    }

    /**
     * Retrieve all time zone identifiers in use at the provided coordinates. Multiple time zones can overlap the
     * provided location in disputed areas such as the South China Sea). The returned time zones  are sorted such that
     * the first entry in the list is the time zone with the smallest land area. These identifiers can be used in modern
     * Java versions to initialize the java.util.TimeZone object and interact with the time zone programmatically.
     *
     * @param degreesLatitude
     *         90.0 is the north pole, -90.0 is the south pole, 0 is the equator.
     * @param degreesLongitude
     *         180.0 to -180.0 such that the White House of the United States is at -77.036586 degrees longitude
     *         (and 38.897670 degrees latitude).
     * @return List of time zone identifiers that are in use at the provided coordinates, if such time zones exists. If
     *         multiple time zones overlap the provided coordinates, as can happen in disputed areas such as the South
     *         China Sea, then all overlapping time zones are return, sorted such that the zone with the smallest
     *         land area is first in the list. If no time zones overlap the provided coordinates, then an empty list
     *         will be returned.
     * @throws IllegalArgumentException
     *         If the provided coordinates are outside of the area index by this instance of the time zone index.
     */
    public List<String> getAllTimeZones(double degreesLatitude, double degreesLongitude) {
        return getOverlappingTimeZones(degreesLatitude, degreesLongitude)
                .map(TimeZone::getZoneId)
                .collect(Collectors.toList());
    }

    private Stream<TimeZone> getOverlappingTimeZones(double degreesLatitude, double degreesLongitude) {
        Point point = new Point(degreesLongitude, degreesLatitude);

        precondition(this.indexedArea.containsExclusive(point.getXY()), "Requested point is outside the indexed area");

        return this.timeZones.stream()
                .filter(t -> GeometryEngine.contains(t.getRegion(), point, SPATIAL_REFERENCE) ||
                        GeometryEngine.touches(t.getRegion(), point, SPATIAL_REFERENCE));
    }

    private static TimeZone convertToEsriBackedTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        Polygon newPolygon = new Polygon();

        for (List<LatLon> region : timeZone.getRegions().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList())) {
            newPolygon.startPath(region.get(0).getLongitude(), region.get(0).getLatitude());
            region.subList(1, region.size())
                    .forEach(p -> newPolygon.lineTo(p.getLongitude(), p.getLatitude()));
        }

        return new TimeZone(timeZone.getTimeZoneId(), newPolygon);
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

    private static Spliterator<TarArchiveEntry> makeSpliterator(TarArchiveInputStream f) {
        return new Spliterators.AbstractSpliterator<TarArchiveEntry>(Long.MAX_VALUE, 0) {
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
                    LOG.error("Unable to read time zone data resource file", e);
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static List<TimeZone> build(Stream<TimeZone> timeZones, Envelope2D indexArea) {
        Polygon indexAreaPolygon = envelopeToPolygon(indexArea);

        return timeZones
                .map(timeZone -> {
                    Envelope2D extents = new Envelope2D();
                    timeZone.getRegion().queryEnvelope2D(extents);

                    return new ExtentsAndTimeZone(extents, timeZone);
                })
                // Throw out anything that doesn't at least partially overlap with the index area.
                .filter(t -> indexArea.isIntersecting(t.extents))
                // Sort smallest area first so we have a deterministic ordering of there is an overlap.
                .sorted(Comparator.comparingDouble(t -> t.timeZone.getRegion().calculateArea2D()))
                // Clip the shape to our indexArea so we don't have to keep large time zones that may only slightly
                // intersect with the region we're indexing.
                .flatMap(t -> {
                    if (indexArea.contains(t.extents)) {
                        return Stream.of(t.timeZone);
                    }

                    GeometryCursor intersectedGeometries = OperatorIntersection.local().execute(
                            new SimpleGeometryCursor(t.timeZone.getRegion()),
                            new SimpleGeometryCursor(indexAreaPolygon),
                            SPATIAL_REFERENCE, null, 4);

                    List<Polygon> list = new ArrayList<>();
                    Geometry geometry;
                    while ((geometry = intersectedGeometries.next()) != null) {
                        if (geometry instanceof Polygon) {
                            list.add((Polygon) geometry);
                        }
                    }

                    return list.stream()
                            .filter(g -> g.getPointCount() > 0)
                            .map(g -> new TimeZone(t.timeZone.getZoneId(), g));
                })
                .collect(Collectors.toList());
    }

    private static void precondition(boolean test, String message) {
        if (!test) {
            throw new IllegalArgumentException(message);
        }
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
