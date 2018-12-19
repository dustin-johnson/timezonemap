package us.dustinj.timezonemap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;

import us.dustinj.timezonemap.data.DataLocator;
import us.dustinj.timezonemap.serialization.Serialization;

@SuppressWarnings("WeakerAccess")
public final class TimeZoneIndex {
    private static final Logger LOG = LoggerFactory.getLogger(TimeZoneIndex.class);

    private final List<TimeZone> timeZones;
    private final Envelope2D indexedArea;

    /**
     * Creates a new instance of {@link TimeZoneIndex} and initializes it with an index for the entire world.
     * This is a blocking long running operation.
     */
    public static TimeZoneIndex forEverywhere() {
        return forRegion(-90.0, -180.0, 90.0, 180.0);
    }

    /**
     * Creates a new instance of {@link TimeZoneIndex} and initializes it with an index valid for anywhere
     * within the provided coordinates. This is a blocking long running operation.
     */
    public static TimeZoneIndex forRegion(double minDegreesLatitude, double minDegreesLongitude,
            double maxDegreesLatitude, double maxDegreesLongitude) {
        try (InputStream inputStream = DataLocator.getDataInputStream();
                TarArchiveInputStream archiveInputStream = new TarArchiveInputStream(
                        new ZstdCompressorInputStream(inputStream))) {

            Stream<TimeZone> timeZones =
                    StreamSupport.stream(Util.makeSpliterator(archiveInputStream), false)
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
                            .map(Util::convertToEsriBackedTimeZone);
            Envelope2D indexArea = new Envelope2D(
                    Math.nextDown(minDegreesLongitude),
                    Math.nextDown(minDegreesLatitude),
                    Math.nextUp(maxDegreesLongitude),
                    Math.nextUp(maxDegreesLatitude));

            return new TimeZoneIndex(Util.build(timeZones, indexArea), indexArea);
        } catch (NullPointerException | IOException e) {
            LOG.error("Unable to read time zone data resource file", e);
            throw new RuntimeException(e);
        }
    }

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

    public List<String> getKnownZoneIds() {
        return timeZones.stream()
                .map(TimeZone::getZoneId)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<TimeZone> getKnownTimeZones() {
        return Collections.unmodifiableList(this.timeZones);
    }

    public Optional<String> getTimeZone(double degreesLatitude, double degreesLongitude) {
        return getAllTimeZones(degreesLatitude, degreesLongitude).stream()
                .findFirst();
    }

    public List<String> getAllTimeZones(double degreesLatitude, double degreesLongitude) {
        Point point = new Point(degreesLongitude, degreesLatitude);

        if (!this.indexedArea.containsExclusive(point.getXY())) {
            throw new IllegalArgumentException("Requested point is outside the indexed area");
        }

        return this.timeZones.stream()
                .filter(t -> GeometryEngine.contains(t.getRegion(), point, Util.SPATIAL_REFERENCE) ||
                        GeometryEngine.touches(t.getRegion(), point, Util.SPATIAL_REFERENCE))
                .map(TimeZone::getZoneId)
                .collect(Collectors.toList());
    }
}
