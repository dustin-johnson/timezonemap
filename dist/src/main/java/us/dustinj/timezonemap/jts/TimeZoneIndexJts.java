package us.dustinj.timezonemap.jts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.dustinj.timezonemap.data.DataLocator;
import us.dustinj.timezonemap.serialization.Serialization;

@SuppressWarnings("WeakerAccess")
public final class TimeZoneIndexJts {
    private static final Logger LOG = LoggerFactory.getLogger(TimeZoneIndexJts.class);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(0.000001), 4326);

    private final List<TimeZoneJts> timeZones;
    private final Envelope indexedArea;

    /**
     * Creates a new instance of {@link TimeZoneIndexJts} and initializes it with an index for the entire world.
     * This is a blocking long running operation that takes about 1-2 seconds on desktop hardware. If performance is
     * a concern, consider using {@link #forRegion(double, double, double, double)}.
     *
     * @return An index that can be used for querying locations anywhere in the world.
     */
    public static TimeZoneIndexJts forEverywhere() {
        return forRegion(-90.0, -180.0, 90.0, 180.0);
    }

    /**
     * Creates a new instance of {@link TimeZoneIndexJts} and initializes it with an index valid for anywhere within the
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
     */
    public static TimeZoneIndexJts forRegion(double minDegreesLatitude, double minDegreesLongitude,
            double maxDegreesLatitude, double maxDegreesLongitude) {
        try (InputStream inputStream = DataLocator.getDataInputStream();
                TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(
                        new ZstdCompressorInputStream(inputStream))) {

            Stream<TimeZoneJts> timeZones =
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
                            .map(TimeZoneIndexJts::convertToJtsBackedTimeZone);
            Envelope indexArea = new Envelope(
                    minDegreesLongitude, maxDegreesLongitude,
                    minDegreesLatitude, maxDegreesLatitude);

            return new TimeZoneIndexJts(build(timeZones, indexArea), indexArea);
        } catch (NullPointerException | IOException e) {
            LOG.error("Unable to read time zone data resource file", e);
            throw new RuntimeException(e);
        }
    }

    private TimeZoneIndexJts(List<TimeZoneJts> timeZones, Envelope indexedArea) {
        LOG.info("Initialized index with {} time zones described with {} points",
                timeZones.size(),
                timeZones.stream()
                        .map(TimeZoneJts::getRegion)
                        .mapToLong(Geometry::getNumPoints)
                        .sum());

        this.timeZones = timeZones;
        this.indexedArea = indexedArea;
    }

    /**
     * An alphabetically sorted (A to Z) list of all time zone identifiers contained in this index. If this index was
     * initialized using {@link #forRegion(double, double, double, double)}, then the returned list represents all
     * time zones that overlap with the coordinates with which this index was initialized.
     * <p>
     * This list represents the full range of identifiers that can be returned by
     * {@link #getTimeZone(double, double)} or {@link #getAllTimeZones(double, double)}.
     *
     * @return A sort list of known time zone identifiers contained in this index.
     */
    public List<String> getKnownZoneIds() {
        return timeZones.stream()
                .map(TimeZoneJts::getZoneId)
                .distinct()
                .collect(Collectors.toList());
    }

    List<TimeZoneJts> getKnownTimeZones() {
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
        return getAllTimeZones(degreesLatitude, degreesLongitude).stream()
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
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(degreesLongitude, degreesLatitude));

        if (!this.indexedArea.covers(point.getCoordinate())) {
            throw new IllegalArgumentException("Requested point is outside the indexed area");
        }

        return this.timeZones.stream()
                .filter(t -> t.getRegion().intersects(point))
                .map(TimeZoneJts::getZoneId)
                .collect(Collectors.toList());
    }

    static TimeZoneJts convertToJtsBackedTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        MultiPolygon newPolygon = geometryFactory.createMultiPolygon(timeZone.getRegions().stream()
                .map(region -> {
                    List<LinearRing> rings = region.stream()
                            .map(ring -> geometryFactory.createLinearRing(ring.stream()
                                    .map(point -> new Coordinate(point.getLongitude(), point.getLatitude()))
                                    .toArray(Coordinate[]::new)))
                            .collect(Collectors.toList());

                    return geometryFactory.createPolygon(
                            rings.get(0),
                            rings.subList(1, rings.size()).toArray(new LinearRing[0]));
                })
                .toArray(Polygon[]::new));

        return new TimeZoneJts(timeZone.getTimeZoneId(), newPolygon);
    }

    public static Spliterator<TarArchiveEntry> makeSpliterator(TarArchiveInputStream f) {
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

    static List<TimeZoneJts> build(Stream<TimeZoneJts> timeZones, Envelope indexArea) {
        Geometry indexAreaGeometry = GEOMETRY_FACTORY.toGeometry(indexArea);

        return timeZones
                // Throw out anything that doesn't at least partially overlap with the index area.
                .filter(t -> indexArea.intersects(t.getRegion().getEnvelopeInternal()))
                // Sort smallest area first so we have a deterministic ordering of there is an overlap.
                .sorted(Comparator.comparingDouble(t -> t.getRegion().getArea()))
                // Clip the shape to our indexArea so we don't have to keep large time zones that may only slightly
                // intersect with the region we're indexing.
                .map(t -> {
                    if (indexArea.covers(t.getRegion().getEnvelopeInternal())) {
                        return t;
                    }

                    System.out.println("processing: " + t.getZoneId());

                    return new TimeZoneJts(t.getZoneId(), t.getRegion().intersection(indexAreaGeometry));
                })
                .collect(Collectors.toList());
    }
}
