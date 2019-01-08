package us.dustinj.timezonemap.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

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
        InputStream inputStream = DataLocator.class.getResourceAsStream("/timezonemap-data.properties");
        checkLoadedInputStream(inputStream);

        return new BufferedReader(new InputStreamReader(inputStream)).lines()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.split("=", 2))
                .map(lineFragments -> new AbstractMap.SimpleEntry<>(lineFragments[0],
                        lineFragments[1].replace("\\", "")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static void checkLoadedInputStream(InputStream stream) {
        if (stream == null) {
            throw new IllegalStateException("Time zone data is not found. Perhaps there is an issue with the class " +
                    "loader or this is being run from the IDE without having built with maven first.");
        }
    }
}
