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
                IntStream.range(1, 1_000)
                        .mapToObj(i -> new LatLon(i * 1000.0f, (float) i))
                        .collect(Collectors.toList()),
                IntStream.range(1, 5)
                        .mapToObj(i -> IntStream.range(1, 500)
                                .mapToObj(ii -> new LatLon(i * ii * 1000.0f, (float) i * ii))
                                .collect(Collectors.toList()))
                        .collect(Collectors.toList()));
        ByteBuffer serializedTimeZone = Serialization.serialize(expectedTimeZone);
        TimeZone actualTimeZone = Serialization.deserialize(serializedTimeZone);

        assertThat(actualTimeZone).isEqualTo(expectedTimeZone);
    }
}