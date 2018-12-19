package us.dustinj.timezonemap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryCursor;
import com.esri.core.geometry.OperatorIntersection;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.SimpleGeometryCursor;
import com.esri.core.geometry.SpatialReference;

import us.dustinj.timezonemap.serialization.LatLon;

final class Util {
    static final SpatialReference SPATIAL_REFERENCE = SpatialReference.create(4326); // WGS84_WKID = 4326
    private static final Logger LOG = LoggerFactory.getLogger(Util.class);

    // Utility class
    private Util() {}

    static TimeZone convertToEsriBackedTimeZone(us.dustinj.timezonemap.serialization.TimeZone timeZone) {
        Polygon newPolygon = new Polygon();

        for (List<LatLon> region : timeZone.getRegions()) {
            newPolygon.startPath(region.get(0).getLongitude(), region.get(0).getLatitude());
            region.subList(1, region.size())
                    .forEach(p -> newPolygon.lineTo(p.getLongitude(), p.getLatitude()));
        }

        return new TimeZone(timeZone.getTimeZoneId(), newPolygon);
    }

    private static Polygon envelopeToPolygon(Envelope2D envelope) {
        Polygon polygon = new Polygon();

        polygon.startPath(envelope.xmin, envelope.ymax); // Upper left
        polygon.lineTo(envelope.xmax, envelope.ymax); // Upper right
        polygon.lineTo(envelope.xmax, envelope.ymin); // Lower right
        polygon.lineTo(envelope.xmin, envelope.ymin); // Lower left
        polygon.lineTo(envelope.xmin, envelope.ymax); // Upper left

        return polygon;
    }

    static Spliterator<TarArchiveEntry> makeSpliterator(TarArchiveInputStream f) {
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

    private static class ExtentsAndTimeZone {
        final Envelope2D extents;
        final TimeZone timeZone;

        ExtentsAndTimeZone(Envelope2D extents, TimeZone timeZone) {
            this.extents = extents;
            this.timeZone = timeZone;
        }
    }

    static List<TimeZone> build(Stream<TimeZone> timeZones, Envelope2D indexArea) {
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
                            .map(g -> new TimeZone(t.timeZone.getZoneId(), g));
                })
                .collect(Collectors.toList());
    }
}
