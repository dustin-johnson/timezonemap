package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Time;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class TimeZoneEngineTest {

    private static class Location {
        final float latitude;
        final float longitude;
        final String timeZoneId;
        final String description;

        Location(float latitude, float longitude, String timeZoneId, String description) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timeZoneId = timeZoneId;
            this.description = description;
        }
    }

    @Test
    public void sanityCheckKnownLocations() {
        List<Location> locations = Stream.of(
                new Location(39.666304f, -7.558607f, "Europe/Lisbon", "Boarder between Spain and Portugal"),
                new Location(39.664104f, -7.535549f, "Europe/Madrid", "Boarder between Spain and Portugal"),
                new Location(39.361070f, -9.407464f, "Europe/Lisbon", "Almost off the coast of Portugal"),
                new Location(39.361532f, -9.440421f, "Europe/Lisbon", "Off the coast of Portugal by 5km"),
                // new Location(39.315657f, -9.920789f, null, "~20km off the coast of Portugal"),
                new Location(36.39823f, -4.35621f, "Europe/Madrid", "Off the coast of Spain by 30km"),
                new Location(36.39258f, -4.36047f, "Etc/GMT", "Off the coast of Spain by 31km"),
                new Location(51.870315f, -8.408394f, "Europe/Dublin", "Cork, Ireland"),
                new Location(35.556645f, 27.203363f, "Europe/Athens", "Tiny Greek island"),
                new Location(55.754136f, 37.620355f, "Europe/Moscow", "Red Square, Moscow, Russia"),
                new Location(19.430056f, -99.136297f, "America/Mexico_City", "Mexico City, Mexico"),
                new Location(45.715940f, -121.512383f, "America/Los_Angeles", "Hood River, Oregon, US"),
                new Location(43.563603f, -117.263646f, "America/Boise", "Weird part of Oregon that's in Mountain Time"),
                new Location(45.684523f, -116.384093f, "America/Boise", "Idaho's north is in Pacific time, " +
                        "and it's south is in Mountain time. This location is barely in the south."),
                new Location(45.637658f, -116.279734f, "America/Los_Angeles", "Idaho, barely in the north"),
                new Location(19.59982f, -155.55946f, "Pacific/Honolulu", "Hawaii"),
                new Location(39.10011f, -94.57814f, "America/Chicago", "Kansas City"),
                new Location(32.77629f, -96.79687f, "America/Chicago", "Dallas, Texas"),
                new Location(40.63969f, -73.94153f, "America/New_York", "New York, New York"),
                new Location(-45.87392f, 170.50348f, "Pacific/Auckland", "Dunedin, New Zealand"),
                new Location(-33.85481f, 151.21644f, "Australia/Sydney", "Sydney, Australia"),
                new Location(-33.04723f, 135.46155f, "Australia/Adelaide", "Wudinna, Australia"),
                new Location(-31.95271f, 115.86046f, "Australia/Perth", "Perth, Australia"),
                new Location(-8.34059f, 115.50450f, "Asia/Makassar", "Mt Agung, Bali, Indonesia"),
                new Location(21.58224f, 39.16403f, "Asia/Riyadh", "Jeddah, Saudi Arabia"),
                new Location(-54.80693f, -68.30734f, "America/Argentina/Ushuaia", "Ushuaia, Argentina"),
                new Location(-54.93413f, -67.61091f, "America/Punta_Arenas", "Puerto Williams, Chile"),
                // new Location(-70.91694f, 54.67198f, "Antarctica/Syowa", "Antarctica"),
                new Location(-47.91847f, 106.91770f, "Etc/GMT-7", "Ulaanbaatar, Mongolia"))
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