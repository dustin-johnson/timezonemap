package us.dustinj.timezonemap.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import us.dustinj.timezonemap.utils.Preconditions;
import us.dustinj.timezonemap.utils.Properties;

@SuppressWarnings("WeakerAccess")
public class DataLocator {

    // Utility class
    private DataLocator() {}

    public static InputStream getDataInputStream() throws IOException {
        InputStream inputStream = DataLocator.class.getResourceAsStream("/" + getMapArchiveFilename());
        checkLoadedInputStream(inputStream);

        return new ZstdCompressorInputStream(inputStream);
    }

    public static String getMapArchiveFilename() {
        return getProperties().get("mapFilename");
    }

    public static String getMapVersion() {
        return getProperties().get("mapVersion");
    }

    private static Map<String, String> getProperties() {
        return Properties.getProperties(DataLocator.class, "timezonemap-data.properties");
    }

    static void checkLoadedInputStream(InputStream stream) {
        Preconditions.checkState(stream != null, "Time zone data is not found. Perhaps there is an issue " +
                "with the class loader or this is being run from the IDE without having built with maven first.");
    }
}
