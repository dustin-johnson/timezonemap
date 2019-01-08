package us.dustinj.timezonemap.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.mockito.Mock;

public class PreconditionsTest {

    @Test
    public void checkState() {
        //noinspection ConstantConditions
        assertThatThrownBy(() -> Preconditions.checkState(false, "Test Message"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Test Message");

        Preconditions.checkState(true, "No exception expected");
    }

    @Test
    public void checkArgument() {
        //noinspection ConstantConditions
        assertThatThrownBy(() -> Preconditions.checkArgument(false, "Test Message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Test Message");

        Preconditions.checkArgument(true, "No exception expected");
    }
}