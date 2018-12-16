package us.dustinj.timezonemap.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.mockito.Mock;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TimeZoneTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(TimeZone.class).verify();
    }
}