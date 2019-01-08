package us.dustinj.timezonemap.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class Properties {

    private Properties() {}

    public static Map<String, String> getProperties(Class<?> resourceOwner, String filename) {
        InputStream inputStream = resourceOwner.getResourceAsStream("/" + filename);

        return new BufferedReader(new InputStreamReader(inputStream)).lines()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.split("=", 2))
                .map(lineFragments -> new AbstractMap.SimpleEntry<>(lineFragments[0],
                        lineFragments[1].replace("\\", "")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
