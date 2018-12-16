package us.dustinj.timezonemap.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.mockito.Mock;

public class DataLocatorTest {

    @Test
    public void noExceptionAndNotNull() {
        assertThat(DataLocator.getDataInputStream()).isNotNull();
    }
}