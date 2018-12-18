package us.dustinj.timezonemap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
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

public final class TimeZoneEngine {
    private static final Logger LOG = LoggerFactory.getLogger(TimeZoneEngine.class);

    private final List<TimeZone> zoneIds;
    private final Envelope2D indexedArea;

    /**
     * Creates a new instance of {@link TimeZoneEngine} and initializes it with an index for the entire world.
     * This is a blocking long running operation.
     */
    public static TimeZoneEngine forEverywhere() {
        return forRegion(-90.0, -180.0, 90.0, 180.0);
    }

    /**
     * Creates a new instance of {@link TimeZoneEngine} and initializes it with an index valid for anywhere
     * within the provided coordinates. This is a blocking long running operation.
     */
    public static TimeZoneEngine forRegion(double minDegreesLatitude, double minDegreesLongitude,
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

            return new TimeZoneEngine(Util.build(timeZones, indexArea), indexArea);
        } catch (NullPointerException | IOException e) {
            LOG.error("Unable to read resource file", e);
            throw new RuntimeException(e);
        }
    }

    private TimeZoneEngine(List<TimeZone> timeZones, Envelope2D indexedArea) {
        LOG.info("Initialized index with {} time zones described with {} points",
                timeZones.size(),
                timeZones.stream()
                        .filter(e -> e.region instanceof Polygon)
                        .mapToLong(e -> ((Polygon) e.region).getPointCount())
                        .sum());

        this.zoneIds = timeZones;
        this.indexedArea = indexedArea;
    }

    public List<String> getKnownZoneIds() {
        return zoneIds.stream()
                .map(e -> e.zoneId)
                .distinct()
                .collect(Collectors.toList());
    }

    public Optional<String> query(double degreesLatitude, double degreesLongitude) {
        Point point = new Point(degreesLongitude, degreesLatitude);

        if (!this.indexedArea.containsExclusive(point.getXY())) {
            throw new IllegalArgumentException("Requested point is outside the indexed area");
        }

        return this.zoneIds.parallelStream()
                .filter(e -> GeometryEngine.contains(e.region, point, Util.SPATIAL_REFERENCE) ||
                        GeometryEngine.touches(e.region, point, Util.SPATIAL_REFERENCE))
                // Sort smallest first, as we want the most specific region if there is an overlap.
                // Note, since we clipped the geometries to the index area when we indexed them, we likely introduced
                // a bug where large regions could look small as they only slightly overlap. I think the only way to
                // solve this is to remove the clipping. Since this is an unlikely defect, I'm ignoring it for now.
                .sorted(Comparator.comparingDouble(e -> e.region.calculateArea2D()))
                .map(e -> e.zoneId)
                .findFirst();
    }
}
