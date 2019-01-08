package us.dustinj.timezonemap.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;
import org.mockito.Mock;

public class PropertiesTest {

    @Test
    public void getProperties() {
        Map<String, String> properties = Properties.getProperties(this.getClass(), "timezonemap-utils.properties");

        assertThat(properties.get("firstProperty")).isEqualTo("first!");
        assertThat(properties.get("secondProperty")).isEqualTo("second:property");
    }
}