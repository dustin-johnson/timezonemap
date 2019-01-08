package us.dustinj.timezonemap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PairTest {

    @Test
    public void accessors() {
        assertThat(new Pair<>(123, "456").getFirst()).isEqualTo(123);
        assertThat(new Pair<>(123, "456").getSecond()).isEqualTo("456");
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Pair.class).verify();
    }

    @Test
    public void testToString() {
        assertThat(new Pair<>(123, "456").toString())
                .contains("123")
                .contains("456");
    }
}