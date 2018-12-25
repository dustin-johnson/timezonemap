package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.junit.Ignore;
import org.junit.Test;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.GeometryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class TimeZoneMapTest {
    private static final TimeZoneMap EVERYWHERE = TimeZoneMap.forEverywhere();

    private static class Location {
        final double latitude;
        final double longitude;
        final List<String> timeZoneIds;
        final String description;

        Location(double latitude, double longitude, String timeZoneId, String description) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timeZoneIds = ImmutableList.of(timeZoneId);
            this.description = description;
        }

        Location(double latitude, double longitude, String timeZoneId1, String timeZoneId2, String description) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timeZoneIds = ImmutableList.of(timeZoneId1, timeZoneId2);
            this.description = description;
        }
    }

    @Test
    public void sanityCheckKnownLocations() {
        List<Location> locations = Stream.of(
                new Location(39.666304, -7.558607, "Europe/Lisbon", "Boarder between Spain and Portugal"),
                new Location(39.664104, -7.535549, "Europe/Madrid", "Boarder between Spain and Portugal"),
                new Location(39.361070, -9.407464, "Europe/Lisbon", "Almost off the coast of Portugal"),
                new Location(39.361532, -9.440421, "Europe/Lisbon", "Off the coast of Portugal by 5km"),
                new Location(39.315657, -9.920789, "Etc/GMT+1", "~20km off the coast of Portugal"),
                new Location(36.39823, -4.35621, "Europe/Madrid", "Off the coast of Spain by 30km"),
                new Location(36.39258, -4.36047, "Etc/GMT", "Off the coast of Spain by 31km"),
                new Location(51.870315, -8.408394, "Europe/Dublin", "Cork, Ireland"),
                new Location(35.556645, 27.203363, "Europe/Athens", "Tiny Greek island"),
                new Location(55.754136, 37.620355, "Europe/Moscow", "Red Square, Moscow, Russia"),
                new Location(19.430056, -99.136297, "America/Mexico_City", "Mexico City, Mexico"),
                new Location(45.715940, -121.512383f, "America/Los_Angeles", "Hood River, Oregon, US"),
                new Location(43.563603, -117.263646f, "America/Boise", "Weird part of Oregon that's in Mountain Time"),
                new Location(45.684523, -116.384093f, "America/Boise", "Idaho's north is in Pacific time, " +
                        "and it's south is in Mountain time. This location is barely in the south."),
                new Location(45.637658, -116.279734f, "America/Los_Angeles", "Idaho, barely in the north"),
                new Location(19.59982, -155.55946, "Pacific/Honolulu", "Hawaii"),
                new Location(39.10011, -94.57814, "America/Chicago", "Kansas City"),
                new Location(32.77629, -96.79687, "America/Chicago", "Dallas, Texas"),
                new Location(40.63969, -73.94153, "America/New_York", "New York, New York"),
                new Location(-45.87392, 170.50348, "Pacific/Auckland", "Dunedin, New Zealand"),
                new Location(-33.85481, 151.21644, "Australia/Sydney", "Sydney, Australia"),
                new Location(-33.04723, 135.46155, "Australia/Adelaide", "Wudinna, Australia"),
                new Location(-31.95271, 115.86046, "Australia/Perth", "Perth, Australia"),
                new Location(-8.34059, 115.50450, "Asia/Makassar", "Mt Agung, Bali, Indonesia"),
                new Location(21.58224, 39.16403, "Asia/Riyadh", "Jeddah, Saudi Arabia"),
                new Location(42.534980, 87.615030, "Asia/Urumqi", "Asia/Shanghai",
                        "Xinjiang, China - Two time zones, Shanghai is much larger and thus should be 2nd"),
                new Location(10.926176, 114.072017, "Asia/Ho_Chi_Minh", "Asia/Shanghai",
                        "South China Sea - Two time zones due this being a highly disputed area"),
                new Location(-54.80693, -68.30734, "America/Argentina/Ushuaia", "Ushuaia, Argentina"),
                new Location(-54.93413, -67.61091, "America/Punta_Arenas", "Puerto Williams, Chile"),
                new Location(-70.91694, 54.67198, "Antarctica/Syowa", "Antarctica"),
                new Location(-47.91847, 106.91770, "Etc/GMT-7", "Ulaanbaatar, Mongolia"))
                .collect(Collectors.toList());

        for (Location location : locations) {
            List<String> everywhereResults =
                    EVERYWHERE.getOverlappingTimeZones(location.latitude, location.longitude).stream()
                            .map(TimeZone::getZoneId)
                            .collect(Collectors.toList());
            Optional<String> everywhereResult =
                    EVERYWHERE.getOverlappingTimeZone(location.latitude, location.longitude).map(TimeZone::getZoneId);
            assertThat(everywhereResults)
                    .as("Everywhere - All time zones - " + location.description)
                    .isEqualTo(location.timeZoneIds);
            assertThat(everywhereResult)
                    .as("Everywhere - Single time zone - " + location.description)
                    .contains(location.timeZoneIds.get(0));

            List<String> scopedResult = TimeZoneMap.forRegion(
                    location.latitude - 1,
                    location.longitude - 1,
                    location.latitude + 1,
                    location.longitude + 1)
                    .getOverlappingTimeZones(location.latitude, location.longitude).stream()
                    .map(TimeZone::getZoneId)
                    .collect(Collectors.toList());
            assertThat(scopedResult)
                    .as("Scoped - " + location.description)
                    .isEqualTo(everywhereResults);
        }
    }

    @Test
    public void forRegion() {
        // Equal latitudes
        assertThatThrownBy(() -> TimeZoneMap.forRegion(1.0, 2.0, 1.0, 4.0))
                .isInstanceOf(IllegalArgumentException.class);

        // Max latitude < min latitude
        assertThatThrownBy(() -> TimeZoneMap.forRegion(1.0, 2.0, 0.0, 4.0))
                .isInstanceOf(IllegalArgumentException.class);

        // Equal longitude
        assertThatThrownBy(() -> TimeZoneMap.forRegion(1.0, 2.0, 3.0, 2.0))
                .isInstanceOf(IllegalArgumentException.class);

        // Max longitude < min longitude
        assertThatThrownBy(() -> TimeZoneMap.forRegion(1.0, 2.0, 3.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Write the indexed time zone regions to disk for diagnostic and sanity checking purposes.
    @Test
    @Ignore
    public void dumpTimeZonesToFiles() throws IOException {
        for (TimeZone timeZone : EVERYWHERE.getTimeZones()) {
            outputJson(timeZone);
        }

        // Build a world.json
        FeatureCollection featureCollection = new FeatureCollection();
        featureCollection.setFeatures(EVERYWHERE.getTimeZones().stream()
                .map(TimeZone::getRegion)
                .map(GeometryEngine::geometryToGeoJson)
                .map(jsonString -> {
                    try {
                        return new ObjectMapper().readValue(jsonString, GeoJsonObject.class);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .map(geoJsonPolygon -> {
                    Feature feature = new Feature();
                    feature.setGeometry(geoJsonPolygon);
                    return feature;
                })
                .collect(Collectors.toList()));
        new ObjectMapper().writeValue(getShapeOutputPath().resolve("World.json").toFile(), featureCollection);
    }

    @Test
    public void testKnownZones() {
        assertThat(EVERYWHERE.getTimeZones().size()).isGreaterThan(400);

        // Small stripe horizontally across the USA
        Envelope2D envelope = new Envelope2D(-123.283836, 40.169102, -77.030765, 40.169103);
        TimeZoneMap scopedEngine = TimeZoneMap.forRegion(envelope.ymin, envelope.xmin, envelope.ymax, envelope.xmax);

        // Accurate results, sorted by land area (smallest first)
        assertThat(scopedEngine.getTimeZones().stream().map(TimeZone::getZoneId))
                .contains("America/Indiana/Indianapolis", "America/Los_Angeles",
                        "America/New_York", "America/Denver", "America/Chicago");

        envelope.inflate(1E-10, 1E-10); // Inflate the envelope just slightly to avoid precision errors.
        scopedEngine.getTimeZones().forEach(t -> {
            Envelope2D regionExtents = new Envelope2D();
            t.getRegion().queryEnvelope2D(regionExtents);

            assertThat(envelope.contains(regionExtents))
                    .as("Time zone " + t.getZoneId() + " is clipped to the indexed region")
                    .isTrue();
        });
    }

    @Test
    public void readmeExample() {
        // Initialize of a region that spans from Germany to Bulgaria.
        // Takes some time (~1-5 seconds) to initialize, so try and initialize only once and keep it.
        TimeZoneMap scopedMap = TimeZoneMap.forRegion(43.5, 8.0, 53.00, 26.0);

        String berlin =
                scopedMap.getOverlappingTimeZone(52.518424, 13.404776).get().getZoneId(); // Returns "Europe/Berlin"
        String prague =
                scopedMap.getOverlappingTimeZone(50.074154, 14.437403).get().getZoneId(); // Returns "Europe/Prague"
        String budapest =
                scopedMap.getOverlappingTimeZone(47.49642, 19.04970).get().getZoneId(); // Returns "Europe/Budapest"
        String milan =
                scopedMap.getOverlappingTimeZone(45.466677, 9.188258).get().getZoneId();   // Returns "Europe/Rome"
        String adriaticSea = scopedMap.getOverlappingTimeZone(44.337, 13.8282).get().getZoneId(); // Returns "Etc/GMT-1"

        // --------------------

        java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(berlin);
        timeZone.observesDaylightTime(); // Returns true

        // --------------------

        assertThat(berlin).isEqualTo("Europe/Berlin");
        assertThat(prague).isEqualTo("Europe/Prague");
        assertThat(budapest).isEqualTo("Europe/Budapest");
        assertThat(milan).isEqualTo("Europe/Rome");
        assertThat(adriaticSea).isEqualTo("Etc/GMT-1");
        assertThat(timeZone.observesDaylightTime()).isTrue();
    }

    @Test
    public void distanceFromBoundary() {
        // GMT+5 on the left side of Jamaica such that the left side of the below region in in GMT+5, but the right
        // side is excerpted due to Jamaica.
        TimeZoneMap scoped = TimeZoneMap.forRegion(
                17.361963, -79.670415,
                19.085664, -77.747903);

        // Right next to the boundary's edge to show that the time zone distance is clipped by the map index region.
        assertThat(scoped.getOverlappingTimeZone(18, -79.67).get().getDistanceFromBoundary(18, -79.67))
                .isCloseTo(44, byLessThan(0.1));

        // Next to the hole created by Jamaica to ensure we deal with the hole correctly. Use a loose tolerance
        // because we don't care about subtle changes in the position of the Jamaica region, just that it exists.
        assertThat(scoped.getOverlappingTimeZone(18.378, -78.57).get().getDistanceFromBoundary(18.378, -78.57))
                .isCloseTo(1418, byLessThan(1_000.0));
    }

    @Test
    public void scopedRegionTest_Africa_Rectangular() {
        TimeZoneMap scoped = TimeZoneMap.forRegion(
                3.97131, 22.78090,
                10.29621, 28.10539);

        assertThatThrownBy(() -> scoped.getOverlappingTimeZone(Math.nextUp(10.29621), 22.78090))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoped.getOverlappingTimeZone(10.29621, Math.nextDown(22.78090)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoped.getOverlappingTimeZone(Math.nextDown(3.97131), 28.10539))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoped.getOverlappingTimeZone(3.97131, Math.nextUp(28.10539)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(scoped.getOverlappingTimeZone(10.29621, 22.78090).map(TimeZone::getZoneId))
                .contains("Africa/Bangui"); // Upper left corner
        assertThat(scoped.getOverlappingTimeZone(3.97131, 28.10539).map(TimeZone::getZoneId))
                .contains("Africa/Lubumbashi"); // Lower right corner

        // Check a few interesting places in this oddly time zoned region
        assertThat(scoped.getOverlappingTimeZone(10.225818, 24.293622).map(TimeZone::getZoneId))
                .contains("Africa/Khartoum");
        assertThat(scoped.getOverlappingTimeZone(10.134434, 25.520542).map(TimeZone::getZoneId))
                .contains("Africa/Juba");
        assertThat(scoped.getOverlappingTimeZone(10.018797, 26.681882).map(TimeZone::getZoneId))
                .contains("Africa/Khartoum");
        assertThat(scoped.getOverlappingTimeZone(5.150331, 27.348469).map(TimeZone::getZoneId))
                .contains("Africa/Bangui");
    }

    @Test
    public void scopedRegionTest_USA_Line() {
        // Small stripe horizontally across the USA
        TimeZoneMap scoped = TimeZoneMap.forRegion(
                40.169102, -123.283836,
                40.169103, -77.030765);

        assertThat(scoped.getOverlappingTimeZone(40.169102, -123.283836).map(TimeZone::getZoneId))
                .contains("America/Los_Angeles");
        assertThat(scoped.getOverlappingTimeZone(40.169102, -106.843598).map(TimeZone::getZoneId))
                .contains("America/Denver");
        assertThat(scoped.getOverlappingTimeZone(40.169102, -93.821612).map(TimeZone::getZoneId))
                .contains("America/Chicago");
        assertThat(scoped.getOverlappingTimeZone(40.169102, -86.164327).map(TimeZone::getZoneId))
                .contains("America/Indiana/Indianapolis");
        assertThat(scoped.getOverlappingTimeZone(40.169102, -77.030765).map(TimeZone::getZoneId))
                .contains("America/New_York");
    }

    @Test
    public void envelopeToPolygon() {
        Envelope2D envelope = new Envelope2D(1.0, 2.0, 3.0, 4.0);

        assertThat(TimeZoneMap.envelopeToPolygon(envelope).calculateArea2D()).isEqualTo(envelope.getArea());
    }

    private Path getShapeOutputPath() throws IOException {
        Path outputPath =
                new File(TimeZoneMapTest.class.getProtectionDomain().getCodeSource().getLocation().getFile())
                        .toPath()                 // /target/test-classes
                        .getParent()              // /target
                        .resolve("shape_output"); // /target/shape_output

        Files.createDirectories(outputPath);

        return outputPath;
    }

    private void outputJson(TimeZone timeZone) throws IOException {
        try {
            Files.write(getShapeOutputPath().resolve(timeZone.getZoneId().replace("/", "_") + ".json"),
                    GeometryEngine.geometryToGeoJson(timeZone.getRegion()).getBytes(StandardCharsets.UTF_8));
        } catch (GeometryException e) {
            System.err.println(e.getMessage() + " - " + timeZone.getZoneId());
        }
    }
}