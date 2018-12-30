package us.dustinj.timezonemap.serialization;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class EnvelopeTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Envelope.class).verify();
    }

}