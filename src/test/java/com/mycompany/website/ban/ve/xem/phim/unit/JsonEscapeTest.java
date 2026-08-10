package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonEscapeTest {
    @Test
    void serializesQuotesBackslashesAndScriptClosingTextAsValidJson() throws Exception {
        String original = "Rạp \"A\" \\ khu </script><script>alert(1)</script>";
        String json = JsonUtil.toJson(Map.of("name", original));
        JsonNode parsed = new ObjectMapper().readTree(json);
        assertEquals(original, parsed.get("name").asText());
    }
}
