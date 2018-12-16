package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.mockito.Mock;

public class TimeZoneEngineTest {
    @Test
    public void sanityCheckKnownLocations() {
        TimeZoneEngine engine = TimeZoneEngine.forEverywhere();

        assertThat(engine.query(39.666304, -7.558607)).contains("Europe/Lisbon"); // Boarder between Spain and Portugal
        assertThat(engine.query(39.664104, -7.535549)).contains("Europe/Madrid"); // Boarder between Spain and Portugal
        assertThat(engine.query(39.361070, -9.407464)).contains("Europe/Lisbon"); // Almost off the coast of Portugal
        assertThat(engine.query(39.361532, -9.440421)).contains("Europe/Lisbon"); // Off the coast of Portugal by 5km
        assertThat(engine.query(39.315657, -9.920789)).isEmpty(); // ~20km off the coast of Portugal
        assertThat(engine.query(51.870315, -8.408394)).contains("Europe/Dublin"); // Cork, Ireland
        assertThat(engine.query(35.556645, 27.203363)).contains("Europe/Athens"); // Tiny Greek island
        assertThat(engine.query(55.754136, 37.620355)).contains("Europe/Moscow"); // Red Square, Moscow, Russia
        assertThat(engine.query(19.430056, -99.136297)).contains("America/Mexico_City"); // Mexico City, Mexico
        assertThat(engine.query(45.715940, -121.512383)).contains("America/Los_Angeles"); // Hood River, Oregon, US

        // Weird part of Oregon that's in Mountain Time
        assertThat(engine.query(43.563603, -117.263646)).contains("America/Boise");
        // Idaho's north is in Pacific time, and it's south is in Mountain time. This location is barely in the south.
        assertThat(engine.query(45.684523, -116.384093)).contains("America/Boise");
        // Idaho, barely in the north
        assertThat(engine.query(45.637658, -116.279734)).contains("America/Los_Angeles");

    }
}