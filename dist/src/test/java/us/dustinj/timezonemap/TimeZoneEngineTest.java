package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;

import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.GeometryException;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;

public class TimeZoneEngineTest {

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
                new Location(39.315657, -9.920789, null, "~20km off the coast of Portugal"),
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

        TimeZoneEngine everywhereEngine = TimeZoneEngine.forEverywhere();
        net.iakovlev.timeshape.TimeZoneEngine everyWhereComparison = net.iakovlev.timeshape.TimeZoneEngine.initialize();

        for (Location location : locations) {
            Optional<String> everywhereResult = everywhereEngine.query(location.latitude, location.longitude);
            assertThat(everywhereResult)
                    .as("Everywhere - " + location.description)
                    .isEqualTo(Optional.ofNullable(location.timeZoneId));
            assertThat(everywhereResult)
                    .as("Everywhere comparison - " + location.description)
                    .isEqualTo(everyWhereComparison.query(location.latitude, location.longitude).map(Object::toString));

            Optional<String> scopedResult = TimeZoneEngine.forRegion(
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

    private static class ExportData<T> {
        final String name;
        final T data;

        public ExportData(String name, T data) {
            this.name = name;
            this.data = data;
        }
    }

    @Test
    @Ignore
    public void name() throws IOException {
        TimeZoneEngine everywhereEngine = TimeZoneEngine.forEverywhere();
        Path baseDirectory = Paths.get("C:\\Users\\Dustin\\Desktop\\testOutput\\");
        Path shapeDirectory = baseDirectory.resolve("Esri Shapes");
        Path geojsonDirectory = baseDirectory.resolve("GeoJson");

        shapeDirectory.toFile().mkdirs();
        geojsonDirectory.toFile().mkdirs();

        List<TimeZone> exportTimeZones =
                Multimaps.index(everywhereEngine.getKnownTimeZones(), t -> t.zoneId).asMap().entrySet().stream()
                        .flatMap(e -> Streams.mapWithIndex(e.getValue().stream(),
                                (t, i) -> new TimeZone(t.zoneId.replace("/", "_") + "_" + i, t.region)))
                        .collect(Collectors.toList());

        for (TimeZone timeZone : exportTimeZones) {
            try {
                Files.write(shapeDirectory.resolve(timeZone.zoneId + ".shp"),
                        GeometryEngine.geometryToEsriShape(timeZone.region));
                Files.write(geojsonDirectory.resolve(timeZone.zoneId + ".json"),
                        GeometryEngine.geometryToGeoJson(timeZone.region).getBytes("UTF-8"));
            } catch (GeometryException e) {
                System.out.println(timeZone.zoneId + " - " + e.getMessage());
                // throw new IllegalStateException("Corrupted geometry '" + timeZone.zoneId + "'", e);
            }
        }
    }

    @Test
    public void scopedRegionTest() {
        TimeZoneEngine scopedEngine = TimeZoneEngine.forRegion(
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