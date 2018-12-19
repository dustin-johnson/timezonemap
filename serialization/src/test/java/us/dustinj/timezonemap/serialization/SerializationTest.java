package us.dustinj.timezonemap.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

public class SerializationTest {

    @Test
    public void serializationRoundTrip() {
        TimeZone expectedTimeZone = new TimeZone("TestTimeZone",
                IntStream.range(1, 5)
                        .mapToObj(polygon -> IntStream.range(1, 3)
                                .mapToObj(ring -> IntStream.range(1, 500)
                                        .mapToObj(point -> new LatLon(polygon * ring * point * 1000.0f,
                                                (float) polygon * ring * point))
                                        .collect(Collectors.toList()))
                                .collect(Collectors.toList()))
                        .collect(Collectors.toList()));
        ByteBuffer serializedTimeZone = Serialization.serialize(expectedTimeZone);
        TimeZone actualTimeZone = Serialization.deserialize(serializedTimeZone);

        assertThat(actualTimeZone).isEqualTo(expectedTimeZone);
    }
}