package us.dustinj.timezonemap.serialization;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeZoneTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(TimeZone.class).verify();
    }
}