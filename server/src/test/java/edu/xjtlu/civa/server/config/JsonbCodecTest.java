package edu.xjtlu.civa.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonbCodecTest {
    @Test void preservesValuesAndRejectsMalformedInputWithoutEchoingIt() {
        var codec = new JsonbCodec(new ObjectMapper());
        var value = codec.object().put("name", "厨房").putNull("imageId");
        assertEquals(value, codec.read(codec.write(value)));
        var duplicate = assertThrows(IllegalArgumentException.class, () -> codec.read("{\"private\":1,\"private\":2}"));
        assertFalse(duplicate.getMessage().contains("private"));
        assertThrows(IllegalArgumentException.class, () -> codec.read("{}{}"));
        assertThrows(IllegalArgumentException.class, () -> codec.read(""));
    }
}
