package us.dustinj.timezonemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import org.junit.Test;

import com.esri.core.geometry.Polygon;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeZoneTest {

    @Test
    public void getDistanceFromBoundary() {
        TimeZone timeZone = UtilTest.getSquareWithIslandTimeZone();

        assertThatThrownBy(() -> timeZone.getDistanceFromBoundary(10, 10))
                .as("Completely outside region")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timeZone.getDistanceFromBoundary(1.8, 1.4))
                .as("In the hole (or mote) around the island")
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(timeZone.getDistanceFromBoundary(2.0f, 2.0f)).isEqualTo(0.0); // On outer boundary, UR corner
        assertThat(timeZone.getDistanceFromBoundary(1.5f, 2.0f)).isEqualTo(0.0); // On outer boundary right side
        assertThat(timeZone.getDistanceFromBoundary(1.5f, 1.5f)).isEqualTo(0.0); // On hole boundary LR corner
        assertThat(timeZone.getDistanceFromBoundary(1.7f, 1.5f)).isEqualTo(0.0); // On hole boundary right side
        assertThat(timeZone.getDistanceFromBoundary(1.7f, 1.2f)).isEqualTo(0.0); // On island boundary UL corner
        assertThat(timeZone.getDistanceFromBoundary(1.7f, 1.25f)).isEqualTo(0.0); // On island boundary top side

        // Close to out right side
        assertThat(timeZone.getDistanceFromBoundary(1.5f, 1.999f)).isCloseTo(111.286, byLessThan(0.001));
        assertThat(timeZone.getDistanceFromBoundary(1.5f, 1.99f)).isCloseTo(1_112.86, byLessThan(0.1));
        assertThat(timeZone.getDistanceFromBoundary(1.5f, 1.9f)).isCloseTo(11_128, byLessThan(1.0));

        // Close to out right side but higher so the longitude to distance conversion is shorter
        assertThat(timeZone.getDistanceFromBoundary(1.8f, 1.999f)).isCloseTo(111.270, byLessThan(0.001));

        // Close to the bottom
        assertThat(timeZone.getDistanceFromBoundary(1.001f, 1.5f)).isCloseTo(110.579, byLessThan(0.001));
        assertThat(timeZone.getDistanceFromBoundary(1.01f, 1.5f)).isCloseTo(1_105.79, byLessThan(0.1));
        assertThat(timeZone.getDistanceFromBoundary(1.1f, 1.5f)).isCloseTo(11_057.9, byLessThan(1.0));

        // In the middle (vertically) between the bottom of the hole and the bottom of the entire time zone
        assertThat(timeZone.getDistanceFromBoundary(1.25f, 1.4f)).isCloseTo(27643.67, byLessThan(0.01));

        // Also in the middle, but vary a bit up and down to demonstrate that either the bottom of the hole or the
        // bottom of the time zone affect the distance value relatively equally.
        assertThat(timeZone.getDistanceFromBoundary(1.30f, 1.4f)).isCloseTo(22114.97, byLessThan(0.03));
        assertThat(timeZone.getDistanceFromBoundary(1.20f, 1.4f)).isCloseTo(22114.97, byLessThan(0.03));

        // Close to the right side of the hole
        assertThat(timeZone.getDistanceFromBoundary(1.65f, 1.501f)).isCloseTo(111.278, byLessThan(0.001));

        // Close to the left side of the island, then the bottom, then right in the middle of the island
        assertThat(timeZone.getDistanceFromBoundary(1.65f, 1.201f)).isCloseTo(111.265, byLessThan(0.001));
        assertThat(timeZone.getDistanceFromBoundary(1.601f, 1.25f)).isCloseTo(110.567, byLessThan(0.001));
        assertThat(timeZone.getDistanceFromBoundary(1.65f, 1.25f)).isCloseTo(5563.676, byLessThan(0.001));
    }

    @Test
    public void equalsContract() {
        Polygon polygonA = new Polygon();
        Polygon polygonB = new Polygon();

        polygonA.startPath(1.23, 4.56);
        polygonA.lineTo(7.89, 0.12);

        EqualsVerifier.forClass(TimeZone.class)
                .withPrefabValues(Polygon.class, polygonA, polygonB)
                .verify();
    }
}