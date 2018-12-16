package us.dustinj.timezonemap.data;

import java.io.InputStream;

public class DataLocator {

    public static InputStream getDataInputStream() {
        InputStream inputStream = DataLocator.class.getResourceAsStream("/timezonemap.tar.zstd");

        if (inputStream == null) {
            throw new IllegalStateException("Time zone data is not found. Perhaps there is an issue with the class " +
                    "loader.");
        }

        return inputStream;
    }
}
