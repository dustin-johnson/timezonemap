package us.dustinj.timezonemap;

import org.junit.Test;

import com.esri.core.geometry.Polygon;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeZoneTest {

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