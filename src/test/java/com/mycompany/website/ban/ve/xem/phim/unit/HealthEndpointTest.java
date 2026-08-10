package com.mycompany.website.ban.ve.xem.phim.unit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEndpointTest {
    @Test
    void healthEndpointIsMappedAndDoesNotExposeConnectionSecrets() throws Exception {
        String webXml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"), StandardCharsets.UTF_8);
        String source = Files.readString(Path.of(
                "src/main/java/com/mycompany/website/ban/ve/xem/phim/api/v1/HealthApiServlet.java"),
                StandardCharsets.UTF_8);
        assertTrue(webXml.contains("<url-pattern>/api/v1/health</url-pattern>"));
        assertTrue(source.contains("status.put(\"db\", \"ok\")"));
        assertFalse(source.contains("getJdbcUrl"));
        assertFalse(source.contains("getPassword"));
    }
}
