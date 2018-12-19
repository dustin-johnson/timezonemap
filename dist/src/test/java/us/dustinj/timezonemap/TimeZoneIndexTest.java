package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.GeometryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;

public class TimeZoneIndexTest {
    private static final TimeZoneIndex EVERYWHERE_INDEX = TimeZoneIndex.forEverywhere();

    private static class Location {
        final double latitude;
        final double longitude;
        final String timeZoneId;
        final String description;

        Location(double latitude, double longitude, String timeZoneId, String description) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timeZoneId = timeZoneId;
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
                new Location(-54.80693, -68.30734, "America/Argentina/Ushuaia", "Ushuaia, Argentina"),
                new Location(-54.93413, -67.61091, "America/Punta_Arenas", "Puerto Williams, Chile"),
                new Location(-70.91694, 54.67198, "Antarctica/Syowa", "Antarctica"),
                new Location(-47.91847, 106.91770, "Etc/GMT-7", "Ulaanbaatar, Mongolia"))
                .collect(Collectors.toList());

        for (Location location : locations) {
            Optional<String> everywhereResult = EVERYWHERE_INDEX.query(location.latitude, location.longitude);
            assertThat(everywhereResult)
                    .as("Everywhere - " + location.description)
                    .isEqualTo(Optional.ofNullable(location.timeZoneId));

            Optional<String> scopedResult = TimeZoneIndex.forRegion(
                    location.latitude - 1,
                    location.longitude - 1,
                    location.latitude + 1,
                    location.longitude + 1)
                    .query(location.latitude, location.longitude);
            assertThat(scopedResult)
                    .as("Scoped - " + location.description)
                    .isEqualTo(everywhereResult);
        }
    }

    // Write the indexed time zone regions to disk for diagnostic and sanity checking purposes.
    @Test
    @Ignore
    public void dumpTimeZonesToFiles() throws IOException {
        Path outputPath =
                new File(TimeZoneIndexTest.class.getProtectionDomain().getCodeSource().getLocation().getFile())
                        .toPath()                 // /target/test-classes
                        .getParent()              // /target
                        .resolve("shape_output"); // /target/shape_output

        //noinspection ResultOfMethodCallIgnored
        outputPath.toFile().mkdirs();

        // Run everything through a multimap so we can have regions with the same timeZoneId and not have file name
        // conflicts. This might not happen, and I don't think it's supposed to, but this is a diagnostic method and
        // it's useful to de-conflict this.
        List<TimeZone> exportTimeZones =
                Multimaps.index(EVERYWHERE_INDEX.getKnownTimeZones(), TimeZone::getZoneId).asMap().entrySet().stream()
                        .flatMap(e -> Streams.mapWithIndex(e.getValue().stream(),
                                (t, i) -> new TimeZone(t.getZoneId().replace("/", "_") + "_" + i, t.getRegion())))
                        .collect(Collectors.toList());

        for (TimeZone timeZone : exportTimeZones) {
            try {
                Files.write(outputPath.resolve(timeZone.getZoneId() + ".json"),
                        GeometryEngine.geometryToGeoJson(timeZone.getRegion()).getBytes(StandardCharsets.UTF_8));
            } catch (GeometryException e) {
                System.err.println(e.getMessage() + " - " + timeZone.getZoneId());
            }
        }

        FeatureCollection featureCollection = new FeatureCollection();
        featureCollection.setFeatures(EVERYWHERE_INDEX.getKnownTimeZones().stream()
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
        new ObjectMapper().writeValue(outputPath.resolve("World.json").toFile(), featureCollection);
    }

    @Test
    public void testKnownZones() {
        assertThat(EVERYWHERE_INDEX.getKnownTimeZones().size()).isGreaterThan(400);
    }

    @Test
    public void testKnownZoneIds() {
        assertThat(EVERYWHERE_INDEX.getKnownZoneIds().size()).isGreaterThan(400);
    }

    @Test
    public void scopedRegionTest() {
        TimeZoneIndex scopedEngine = TimeZoneIndex.forRegion(
                3.97131, 22.78090,
                10.29621, 28.10539);

        assertThatThrownBy(() -> scopedEngine.query(Math.nextUp(10.29621), 22.78090))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopedEngine.query(10.29621, Math.nextDown(22.78090)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopedEngine.query(Math.nextDown(3.97131), 28.10539))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopedEngine.query(3.97131, Math.nextUp(28.10539)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(scopedEngine.query(10.29621, 22.78090)).contains("Africa/Bangui"); // Upper left corner
        assertThat(scopedEngine.query(3.97131, 28.10539)).contains("Africa/Lubumbashi"); // Lower right corner

        assertThat(scopedEngine.query(10.225818, 24.293622)).contains("Africa/Khartoum");
    }
}