package us.dustinj.timezonemap.serialization;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class LatLonTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(LatLon.class).verify();
    }
}