package us.dustinj.timezonemap.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class Properties {

    private Properties() {}

    public static Map<String, String> getProperties(Class<?> resourceOwner, String filename) {
        InputStream inputStream = resourceOwner.getResourceAsStream("/" + filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        Map<String, String> map = new HashMap<>();
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("#")) {
                    String[] split = line.split("=", 2);
                    map.put(split[0], split[1].replace("\\", ""));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return map;
    }
}
