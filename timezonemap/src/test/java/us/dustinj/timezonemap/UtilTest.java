package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.junit.Test;

import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.google.common.collect.ImmutableList;

import us.dustinj.timezonemap.serialization.LatLon;

public class UtilTest {

    static TimeZone getSquareWithIslandTimeZone() {
        return Util.convertToEsriBackedTimeZone(
                new us.dustinj.timezonemap.serialization.TimeZone("Square with island", Collections.singletonList(
                        /*-
                        This time zone region looks something like this, with a hole in the upper left quadrant and
                        an island in the hole:
                        +------------------+
                        |+-------+         |
                        || hole  |         |
                        || []    |         |
                        |+-------+         |
                        |                  |
                        |                  |
                        |                  |
                        +------------------+
                         */
                        ImmutableList.of(
                                // Outer boundary. Coordinates go clockwise.
                                ImmutableList.of(
                                        new LatLon(2f, 1f), // Upper left
                                        new LatLon(2f, 2f), // Upper right
                                        new LatLon(1f, 2f), // Lower right
                                        new LatLon(1f, 1f)  // Lower left
                                ),
                                // Inner hole in the upper left quadrant of the outer boundary.
                                // Coordinates go counter-clockwise.
                                ImmutableList.of(
                                        new LatLon(1.9f, 1.1f), // Upper left
                                        new LatLon(1.5f, 1.1f), // Lower left
                                        new LatLon(1.5f, 1.5f), // Lower right
                                        new LatLon(1.9f, 1.5f)  // Upper right
                                ),
                                // Island in the hole. Coordinates go clockwise.
                                ImmutableList.of(
                                        new LatLon(1.7f, 1.2f), // Upper left
                                        new LatLon(1.7f, 1.3f), // Upper right
                                        new LatLon(1.6f, 1.3f), // Lower right
                                        new LatLon(1.6f, 1.2f)  // Lower left
                                )
                        )
                )));
    }

    @Test
    public void containsInclusive() {
        Polygon squareWithIsland = getSquareWithIslandTimeZone().getRegion();

        // Right on boundaries (sides)
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1f, 1.65f))).isTrue(); // On outer left side
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.1f, 1.65f))).isTrue(); // On hole left side
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.2f, 1.65f))).isTrue(); // On island left side

        // Right on boundaries (corners)
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1f, 1f))).isTrue(); // Outer LL
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.5f, 1.5f))).isTrue(); // Hole UR
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.3f, 1.6f))).isTrue(); // Island LR

        // Inside area
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.3f, 1.3f))).isTrue(); // Main area
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.25f, 1.65f))).isTrue(); // Island

        // Outside
        assertThat(Util.containsInclusive(squareWithIsland, new Point(10f, 10f))).isFalse(); // Main area
        assertThat(Util.containsInclusive(squareWithIsland, new Point(1.4f, 1.8f))).isFalse(); // Hole area
    }

    @Test
    public void precondition() {
        Util.precondition(true, "Ensure no exception is thrown");
        //noinspection ConstantConditions
        assertThatThrownBy(() -> Util.precondition(false, "Exception text"))
                .hasMessage("Exception text")
                .isInstanceOf(IllegalArgumentException.class);
    }
}