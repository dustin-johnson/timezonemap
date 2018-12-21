package us.dustinj.timezonemap.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.InputStream;

import org.junit.Test;
import org.mockito.Mock;

public class DataLocatorTest {

    @Test
    public void getDataInputStream() {
        assertThat(DataLocator.getDataInputStream()).isNotNull();
    }

    @Test
    public void getMapArchiveFilename() {
        assertThat(DataLocator.getMapArchiveFilename())
                .contains(DataLocator.getMapVersion())
                .endsWith(".tar.zstd");
    }

    @Test
    public void getMapVersion() {
        assertThat(DataLocator.getMapVersion()).isNotNull();
    }

    @Test
    public void checkLoadedInputStream() {
        //noinspection ConstantConditions
        assertThatThrownBy(() -> DataLocator.checkLoadedInputStream(null))
                .isInstanceOf(IllegalStateException.class);

        // Ensure no exception
        DataLocator.checkLoadedInputStream(mock(InputStream.class));
    }
}